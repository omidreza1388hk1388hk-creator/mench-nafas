import { BadRequestException, PayloadTooLargeException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AttachmentsService, UploadAttachmentInput } from './attachments.service';
import { AttachmentsRepository, AttachmentRow } from './attachments.repository';
import { StorageProvider } from '../../storage/storage-provider.interface';

// sharp does real native image processing — mocked here so this test
// suite is fast and deterministic and doesn't need a real JPEG/PNG byte
// stream to exercise AttachmentsService's own logic (size limits, mime
// routing, key generation). Image-processing correctness itself is
// sharp's responsibility, not this test's.
jest.mock('sharp', () => {
  return jest.fn(() => ({
    metadata: jest.fn().mockResolvedValue({ width: 800, height: 600 }),
    resize: jest.fn().mockReturnThis(),
    webp: jest.fn().mockReturnThis(),
    toBuffer: jest.fn().mockResolvedValue(Buffer.from('fake-thumbnail-bytes')),
  }));
});

describe('AttachmentsService', () => {
  let service: AttachmentsService;
  let repository: jest.Mocked<AttachmentsRepository>;
  let storageProvider: jest.Mocked<StorageProvider>;
  let config: ConfigService;

  const uploaderId = 'user-1';
  const conversationId = 'conv-1';

  // Base input with every required field present — individual tests
  // spread over just the fields they care about, so adding a new required
  // field to UploadAttachmentInput only means updating this one object,
  // not every test in the file.
  const baseInput: UploadAttachmentInput = {
    uploaderId,
    conversationId,
    originalFilename: 'file.bin',
    mimeType: 'application/octet-stream',
    buffer: Buffer.from('bytes'),
    durationMs: null,
    thumbnailBuffer: null,
    clientReportedWidthPx: null,
    clientReportedHeightPx: null,
  };

  beforeEach(() => {
    repository = { create: jest.fn(), findById: jest.fn() } as unknown as jest.Mocked<AttachmentsRepository>;
    storageProvider = { put: jest.fn(), get: jest.fn(), delete: jest.fn() };
    config = new ConfigService({ MAX_ATTACHMENT_SIZE_BYTES: 1024 * 1024 });

    storageProvider.put.mockResolvedValue({ key: 'irrelevant', contentType: 'irrelevant', sizeBytes: 0 });
    repository.create.mockImplementation(async (input) => ({
      id: 'att-1',
      uploader_id: input.uploaderId,
      conversation_id: input.conversationId,
      kind: input.kind,
      storage_key: input.storageKey,
      thumbnail_storage_key: input.thumbnailStorageKey,
      original_filename: input.originalFilename,
      mime_type: input.mimeType,
      size_bytes: String(input.sizeBytes),
      width_px: input.widthPx,
      height_px: input.heightPx,
      duration_ms: input.durationMs,
      created_at: new Date(),
    }) as AttachmentRow);

    service = new AttachmentsService(repository, storageProvider, config);
  });

  it('rejects an empty file', async () => {
    await expect(
      service.upload({ ...baseInput, originalFilename: 'empty.txt', mimeType: 'text/plain', buffer: Buffer.alloc(0) }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('rejects a file over the configured size limit', async () => {
    await expect(
      service.upload({
        ...baseInput,
        originalFilename: 'big.bin',
        buffer: Buffer.alloc(1024 * 1024 + 1),
      }),
    ).rejects.toBeInstanceOf(PayloadTooLargeException);
    expect(storageProvider.put).not.toHaveBeenCalled();
  });

  it('classifies an allowed image mime type as kind=image and generates a thumbnail', async () => {
    const result = await service.upload({
      ...baseInput,
      originalFilename: 'photo.jpg',
      mimeType: 'image/jpeg',
      buffer: Buffer.from('fake-jpeg-bytes'),
    });

    expect(result.kind).toBe('image');
    expect(result.width_px).toBe(800);
    expect(result.height_px).toBe(600);
    expect(result.thumbnail_storage_key).not.toBeNull();
    // original + thumbnail = two separate stored objects
    expect(storageProvider.put).toHaveBeenCalledTimes(2);
  });

  it('classifies a non-image mime type as kind=file with no thumbnail, and never touches sharp', async () => {
    const result = await service.upload({
      ...baseInput,
      originalFilename: 'report.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('fake-pdf-bytes'),
    });

    expect(result.kind).toBe('file');
    expect(result.width_px).toBeNull();
    expect(result.thumbnail_storage_key).toBeNull();
    expect(storageProvider.put).toHaveBeenCalledTimes(1);
  });

  it('never derives the storage key from the client-supplied filename', async () => {
    const result = await service.upload({
      ...baseInput,
      originalFilename: '../../../etc/passwd',
      mimeType: 'text/plain',
      buffer: Buffer.from('hi'),
    });

    expect(result.storage_key).not.toContain('..');
    expect(result.storage_key).not.toContain('etc/passwd');
    expect(result.original_filename).toBe('../../../etc/passwd'); // stored verbatim for display only, never used as a path
  });

  it('classifies an allowed audio mime type as kind=audio, stores durationMs, and never touches sharp', async () => {
    const result = await service.upload({
      ...baseInput,
      originalFilename: 'voice-note.m4a',
      mimeType: 'audio/mp4',
      buffer: Buffer.from('fake-m4a-bytes'),
      durationMs: 4200,
    });

    expect(result.kind).toBe('audio');
    expect(result.duration_ms).toBe(4200);
    expect(result.width_px).toBeNull(); // sharp never runs for audio
    expect(result.thumbnail_storage_key).toBeNull();
    expect(storageProvider.put).toHaveBeenCalledTimes(1);
  });

  it('rejects an audio upload with no durationMs', async () => {
    await expect(
      service.upload({
        ...baseInput,
        originalFilename: 'voice-note.m4a',
        mimeType: 'audio/mp4',
        buffer: Buffer.from('fake-m4a-bytes'),
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(storageProvider.put).not.toHaveBeenCalled();
  });

  it('rejects an audio upload whose reported duration exceeds the sanity cap', async () => {
    await expect(
      service.upload({
        ...baseInput,
        originalFilename: 'voice-note.m4a',
        mimeType: 'audio/mp4',
        buffer: Buffer.from('fake-m4a-bytes'),
        durationMs: 11 * 60 * 1000, // 11 minutes, over the 10-minute cap
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('classifies an allowed video mime type as kind=video, uses client-reported dimensions/duration, and resizes the client-provided thumbnail via sharp', async () => {
    const result = await service.upload({
      ...baseInput,
      originalFilename: 'clip.mp4',
      mimeType: 'video/mp4',
      buffer: Buffer.from('fake-mp4-bytes'),
      durationMs: 8000,
      clientReportedWidthPx: 1920,
      clientReportedHeightPx: 1080,
      thumbnailBuffer: Buffer.from('fake-jpeg-frame-bytes'),
    });

    expect(result.kind).toBe('video');
    expect(result.duration_ms).toBe(8000);
    expect(result.width_px).toBe(1920);
    expect(result.height_px).toBe(1080);
    expect(result.thumbnail_storage_key).not.toBeNull();
    // original video + resized thumbnail = two separate stored objects
    expect(storageProvider.put).toHaveBeenCalledTimes(2);
  });

  it('uploads a video successfully even with no thumbnail (degraded, not blocked)', async () => {
    const result = await service.upload({
      ...baseInput,
      originalFilename: 'clip.mp4',
      mimeType: 'video/mp4',
      buffer: Buffer.from('fake-mp4-bytes'),
      durationMs: 8000,
    });

    expect(result.kind).toBe('video');
    expect(result.thumbnail_storage_key).toBeNull();
    expect(storageProvider.put).toHaveBeenCalledTimes(1);
  });

  it('rejects a video upload with no durationMs', async () => {
    await expect(
      service.upload({
        ...baseInput,
        originalFilename: 'clip.mp4',
        mimeType: 'video/mp4',
        buffer: Buffer.from('fake-mp4-bytes'),
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('rejects a video upload whose reported duration exceeds the sanity cap', async () => {
    await expect(
      service.upload({
        ...baseInput,
        originalFilename: 'clip.mp4',
        mimeType: 'video/mp4',
        buffer: Buffer.from('fake-mp4-bytes'),
        durationMs: 6 * 60 * 1000, // 6 minutes, over the 5-minute cap
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });
});
