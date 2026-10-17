import { BadRequestException, Injectable, NotFoundException, PayloadTooLargeException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { AttachmentsService } from './attachments.service';
import { AttachmentRow } from './attachments.repository';

const MIN_CHUNK_BYTES = 256 * 1024;
const MAX_CHUNK_BYTES = 8 * 1024 * 1024;
const STALE_AFTER_MS = 24 * 60 * 60 * 1000;

interface UploadMeta {
  userId: string;
  conversationId: string;
  originalFilename: string;
  mimeType: string;
  totalSize: number;
  chunkSize: number;
  durationMs: number | null;
  widthPx: number | null;
  heightPx: number | null;
  createdAt: number;
}

export interface InitChunkedUploadInput {
  userId: string;
  conversationId: string;
  originalFilename: string;
  mimeType: string;
  totalSize: number;
  chunkSize: number;
  durationMs?: number | null;
  widthPx?: number | null;
  heightPx?: number | null;
}

export interface ChunkedUploadStatus {
  uploadId: string;
  totalSize: number;
  chunkSize: number;
  totalChunks: number;
  received: number[];
}

/**
 * Resumable uploads for big files (Phase 1B). The client sends a file in chunks; a dropped connection
 * resumes from the chunks the server already has. Chunks are staged on local temp disk, then handed to the normal
 * [AttachmentsService.upload] so size limits, thumbnails and storage are exactly the same as a one-shot upload.
 * Limitation: staging is per server instance (sticky routing or a shared volume is needed when scaled out).
 */
@Injectable()
export class ChunkedUploadService {
  private readonly root = path.join(os.tmpdir(), 'mench-chunked');

  constructor(
    private readonly attachments: AttachmentsService,
    private readonly config: ConfigService,
  ) {}

  async init(input: InitChunkedUploadInput): Promise<ChunkedUploadStatus> {
    const maxSize = this.config.get<number>('MAX_ATTACHMENT_SIZE_BYTES') as number;
    if (!Number.isInteger(input.totalSize) || input.totalSize <= 0) throw new BadRequestException('Invalid totalSize');
    if (input.totalSize > maxSize) {
      throw new PayloadTooLargeException(`Attachment exceeds the ${Math.floor(maxSize / (1024 * 1024))}MB limit`);
    }
    if (input.chunkSize < MIN_CHUNK_BYTES || input.chunkSize > MAX_CHUNK_BYTES) {
      throw new BadRequestException('chunkSize must be between 256 KB and 8 MB');
    }
    await this.purgeStale();
    const uploadId = crypto.randomUUID();
    const meta: UploadMeta = {
      userId: input.userId,
      conversationId: input.conversationId,
      originalFilename: input.originalFilename.slice(0, 255),
      mimeType: input.mimeType,
      totalSize: input.totalSize,
      chunkSize: input.chunkSize,
      durationMs: input.durationMs ?? null,
      widthPx: input.widthPx ?? null,
      heightPx: input.heightPx ?? null,
      createdAt: Date.now(),
    };
    await fs.promises.mkdir(this.dir(uploadId), { recursive: true });
    await fs.promises.writeFile(path.join(this.dir(uploadId), 'meta.json'), JSON.stringify(meta));
    return this.statusOf(uploadId, meta);
  }

  async status(userId: string, conversationId: string, uploadId: string): Promise<ChunkedUploadStatus> {
    const meta = await this.loadMeta(userId, conversationId, uploadId);
    return this.statusOf(uploadId, meta);
  }

  async putChunk(userId: string, conversationId: string, uploadId: string, index: number, data: Buffer): Promise<ChunkedUploadStatus> {
    const meta = await this.loadMeta(userId, conversationId, uploadId);
    const totalChunks = Math.ceil(meta.totalSize / meta.chunkSize);
    if (!Number.isInteger(index) || index < 0 || index >= totalChunks) throw new BadRequestException('Chunk index out of range');
    const expected = index === totalChunks - 1 ? meta.totalSize - meta.chunkSize * (totalChunks - 1) : meta.chunkSize;
    if (data.length !== expected) throw new BadRequestException(`Chunk ${index} must be ${expected} bytes`);
    // Write to a temp name then rename, so a half-written chunk is never counted as received.
    const target = path.join(this.dir(uploadId), `${index}.part`);
    const tmp = `${target}.tmp`;
    await fs.promises.writeFile(tmp, data);
    await fs.promises.rename(tmp, target);
    return this.statusOf(uploadId, meta);
  }

  async putThumbnail(userId: string, conversationId: string, uploadId: string, data: Buffer): Promise<void> {
    await this.loadMeta(userId, conversationId, uploadId);
    if (data.length === 0 || data.length > 2 * 1024 * 1024) throw new BadRequestException('Invalid thumbnail');
    await fs.promises.writeFile(path.join(this.dir(uploadId), 'thumbnail.bin'), data);
  }

  async complete(userId: string, conversationId: string, uploadId: string): Promise<AttachmentRow> {
    const meta = await this.loadMeta(userId, conversationId, uploadId);
    const status = await this.statusOf(uploadId, meta);
    if (status.received.length !== status.totalChunks) {
      throw new BadRequestException('Upload incomplete: ' + (status.totalChunks - status.received.length) + ' chunk(s) missing');
    }
    const parts: Buffer[] = [];
    for (let i = 0; i < status.totalChunks; i++) parts.push(await fs.promises.readFile(path.join(this.dir(uploadId), `${i}.part`)));
    const buffer = Buffer.concat(parts);
    const thumbPath = path.join(this.dir(uploadId), 'thumbnail.bin');
    const thumbnailBuffer = fs.existsSync(thumbPath) ? await fs.promises.readFile(thumbPath) : null;

    const attachment = await this.attachments.upload({
      uploaderId: userId,
      conversationId,
      originalFilename: meta.originalFilename,
      mimeType: meta.mimeType,
      buffer,
      durationMs: meta.durationMs,
      thumbnailBuffer,
      clientReportedWidthPx: meta.widthPx,
      clientReportedHeightPx: meta.heightPx,
    });
    await this.remove(uploadId);
    return attachment;
  }

  /** Deletes staging folders older than 24 h (abandoned uploads). Cheap: runs on every init. */
  async purgeStale(): Promise<number> {
    let removed = 0;
    let names: string[] = [];
    try { names = await fs.promises.readdir(this.root); } catch { return 0; }
    for (const name of names) {
      try {
        const stat = await fs.promises.stat(path.join(this.root, name));
        if (Date.now() - stat.mtimeMs > STALE_AFTER_MS) { await this.remove(name); removed++; }
      } catch { /* raced with another cleanup */ }
    }
    return removed;
  }

  private dir(uploadId: string): string {
    // uploadId is a UUID validated by the controller; the check here is defence in depth against path traversal.
    if (!/^[0-9a-fA-F-]{36}$/.test(uploadId)) throw new BadRequestException('Invalid upload id');
    return path.join(this.root, uploadId);
  }

  private async remove(uploadId: string): Promise<void> {
    await fs.promises.rm(this.dir(uploadId), { recursive: true, force: true });
  }

  private async loadMeta(userId: string, conversationId: string, uploadId: string): Promise<UploadMeta> {
    let meta: UploadMeta;
    try {
      meta = JSON.parse(await fs.promises.readFile(path.join(this.dir(uploadId), 'meta.json'), 'utf8')) as UploadMeta;
    } catch {
      throw new NotFoundException('Upload not found');
    }
    // 404, not 403: same policy as the rest of the API, never reveal that someone else's upload exists.
    if (meta.userId !== userId || meta.conversationId !== conversationId) throw new NotFoundException('Upload not found');
    return meta;
  }

  private async statusOf(uploadId: string, meta: UploadMeta): Promise<ChunkedUploadStatus> {
    const totalChunks = Math.ceil(meta.totalSize / meta.chunkSize);
    const files = await fs.promises.readdir(this.dir(uploadId));
    const received = files
      .filter((f) => /^\d+\.part$/.test(f))
      .map((f) => parseInt(f, 10))
      .filter((i) => i < totalChunks)
      .sort((a, b) => a - b);
    return { uploadId, totalSize: meta.totalSize, chunkSize: meta.chunkSize, totalChunks, received };
  }
}
