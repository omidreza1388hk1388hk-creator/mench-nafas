import { BadRequestException, Inject, Injectable, PayloadTooLargeException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as crypto from 'crypto';
import sharp from 'sharp';
import { AttachmentsRepository, AttachmentRow } from './attachments.repository';
import { STORAGE_PROVIDER } from '../../storage/storage.module';
import { StorageProvider } from '../../storage/storage-provider.interface';

const ALLOWED_IMAGE_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const ALLOWED_AUDIO_MIME_TYPES = new Set(['audio/mp4', 'audio/m4a', 'audio/aac', 'audio/webm', 'audio/ogg', 'audio/mpeg']);
const ALLOWED_VIDEO_MIME_TYPES = new Set(['video/mp4', 'video/webm', 'video/3gpp']);
const THUMBNAIL_MAX_DIMENSION_PX = 480;
const MAX_VOICE_DURATION_MS = 10 * 60 * 1000; // 10 minutes — a sanity cap, not a product decision carved in stone
const MAX_VIDEO_DURATION_MS = 5 * 60 * 1000; // 5 minutes — same reasoning

export interface UploadAttachmentInput {
  uploaderId: string;
  conversationId: string;
  originalFilename: string;
  mimeType: string;
  buffer: Buffer;
  /** Only meaningful (and only trusted) for audio/video — reported by the client, which timed its own recording/knows the file it picked. Not independently verified server-side; see docs/SECURITY.md. */
  durationMs: number | null;
  /**
   * Video only. Extracted client-side (MediaMetadataRetriever) and
   * uploaded as a normal JPEG — the server never runs video processing
   * (no ffmpeg dependency); this buffer is resized by sharp exactly like
   * any other image thumbnail. Width/height are also client-reported for
   * the same reason as duration.
   */
  thumbnailBuffer: Buffer | null;
  clientReportedWidthPx: number | null;
  clientReportedHeightPx: number | null;
}

@Injectable()
export class AttachmentsService {
  constructor(
    private readonly attachmentsRepository: AttachmentsRepository,
    @Inject(STORAGE_PROVIDER) private readonly storageProvider: StorageProvider,
    private readonly config: ConfigService,
  ) {}

  async upload(input: UploadAttachmentInput): Promise<AttachmentRow> {
    const maxSize = this.config.get<number>('MAX_ATTACHMENT_SIZE_BYTES') as number;
    if (input.buffer.length > maxSize) {
      throw new PayloadTooLargeException(
        `Attachment exceeds the ${Math.floor(maxSize / (1024 * 1024))}MB limit`,
      );
    }
    if (input.buffer.length === 0) {
      throw new BadRequestException('Empty file');
    }

    const isImage = ALLOWED_IMAGE_MIME_TYPES.has(input.mimeType);
    const isAudio = ALLOWED_AUDIO_MIME_TYPES.has(input.mimeType);
    const isVideo = ALLOWED_VIDEO_MIME_TYPES.has(input.mimeType);
    const kind: 'image' | 'file' | 'audio' | 'video' = isImage
      ? 'image'
      : isAudio
        ? 'audio'
        : isVideo
          ? 'video'
          : 'file';

    let durationMs: number | null = null;
    if (isAudio) {
      if (input.durationMs === null || input.durationMs <= 0) {
        throw new BadRequestException('durationMs is required for audio attachments');
      }
      if (input.durationMs > MAX_VOICE_DURATION_MS) {
        throw new BadRequestException(`Voice messages are capped at ${MAX_VOICE_DURATION_MS / 60_000} minutes`);
      }
      durationMs = input.durationMs;
    }
    if (isVideo) {
      if (input.durationMs === null || input.durationMs <= 0) {
        throw new BadRequestException('durationMs is required for video attachments');
      }
      if (input.durationMs > MAX_VIDEO_DURATION_MS) {
        throw new BadRequestException(`Videos are capped at ${MAX_VIDEO_DURATION_MS / 60_000} minutes`);
      }
      durationMs = input.durationMs;
    }

    // Server-generated, not derived from the client-supplied filename —
    // this is what makes LocalDiskStorageProvider's path-traversal guard
    // a defense-in-depth backstop rather than the only thing standing
    // between a crafted filename and an arbitrary write path.
    const storageKey = `${crypto.randomUUID()}${extensionFor(input.mimeType)}`;

    let widthPx: number | null = null;
    let heightPx: number | null = null;
    let thumbnailStorageKey: string | null = null;

    if (isImage) {
      const metadata = await sharp(input.buffer).metadata();
      widthPx = metadata.width ?? null;
      heightPx = metadata.height ?? null;

      const thumbnailBuffer = await sharp(input.buffer)
        .resize(THUMBNAIL_MAX_DIMENSION_PX, THUMBNAIL_MAX_DIMENSION_PX, { fit: 'inside', withoutEnlargement: true })
        .webp({ quality: 70 })
        .toBuffer();

      thumbnailStorageKey = `${crypto.randomUUID()}.webp`;
      await this.storageProvider.put(thumbnailStorageKey, thumbnailBuffer, 'image/webp');
    } else if (isVideo) {
      // Dimensions are whatever the client reported (see the field's
      // doc comment) — never derived from the thumbnail frame's own
      // size, which may differ from the actual video track dimensions.
      widthPx = input.clientReportedWidthPx;
      heightPx = input.clientReportedHeightPx;

      if (input.thumbnailBuffer && input.thumbnailBuffer.length > 0) {
        const resizedThumbnail = await sharp(input.thumbnailBuffer)
          .resize(THUMBNAIL_MAX_DIMENSION_PX, THUMBNAIL_MAX_DIMENSION_PX, { fit: 'inside', withoutEnlargement: true })
          .webp({ quality: 70 })
          .toBuffer();

        thumbnailStorageKey = `${crypto.randomUUID()}.webp`;
        await this.storageProvider.put(thumbnailStorageKey, resizedThumbnail, 'image/webp');
      }
      // No client-provided thumbnail is a degraded-but-valid state (e.g.
      // frame extraction failed on-device) — the video still uploads,
      // just without a thumbnail, rather than blocking the whole send.
    }
    // Audio gets no thumbnail — sharp only handles images. A waveform
    // preview image would be a real, separate feature to add later, not
    // something to fake here with a placeholder.

    await this.storageProvider.put(storageKey, input.buffer, input.mimeType);

    return this.attachmentsRepository.create({
      uploaderId: input.uploaderId,
      conversationId: input.conversationId,
      kind,
      storageKey,
      thumbnailStorageKey,
      originalFilename: input.originalFilename,
      mimeType: input.mimeType,
      sizeBytes: input.buffer.length,
      widthPx,
      heightPx,
      durationMs,
    });
  }

  findById(id: string): Promise<AttachmentRow | null> {
    return this.attachmentsRepository.findById(id);
  }

  getContent(key: string): Promise<Buffer> {
    return this.storageProvider.get(key);
  }
}

function extensionFor(mimeType: string): string {
  switch (mimeType) {
    case 'image/jpeg':
      return '.jpg';
    case 'image/png':
      return '.png';
    case 'image/webp':
      return '.webp';
    case 'audio/mp4':
    case 'audio/m4a':
      return '.m4a';
    case 'audio/aac':
      return '.aac';
    case 'audio/webm':
      return '.webm';
    case 'audio/ogg':
      return '.ogg';
    case 'audio/mpeg':
      return '.mp3';
    case 'video/mp4':
      return '.mp4';
    case 'video/webm':
      return '.webm';
    case 'video/3gpp':
      return '.3gp';
    default:
      return '';
  }
}
