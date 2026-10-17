import { BadRequestException, NotFoundException, PayloadTooLargeException } from '@nestjs/common';
import { ChunkedUploadService } from './chunked-upload.service';

describe('ChunkedUploadService', () => {
  const upload = jest.fn();
  const attachments = { upload } as never;
  const config = { get: jest.fn().mockReturnValue(10 * 1024 * 1024) } as never;
  const service = new ChunkedUploadService(attachments, config);
  const CHUNK = 256 * 1024;
  const base = {
    userId: 'u1',
    conversationId: 'c1',
    originalFilename: 'v.mp4',
    mimeType: 'video/mp4',
    durationMs: 1000,
  };

  beforeEach(() => upload.mockReset());

  it('rejects files over the configured limit and bad chunk sizes', async () => {
    await expect(
      service.init({ ...base, totalSize: 11 * 1024 * 1024, chunkSize: CHUNK }),
    ).rejects.toBeInstanceOf(PayloadTooLargeException);
    await expect(service.init({ ...base, totalSize: 1000, chunkSize: 10 })).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('resumes: reports received chunks, rejects wrong sizes, completes only when all chunks are present', async () => {
    const total = CHUNK * 2 + 100;
    const s = await service.init({ ...base, totalSize: total, chunkSize: CHUNK });
    expect(s.totalChunks).toBe(3);
    await expect(
      service.putChunk('u1', 'c1', s.uploadId, 0, Buffer.alloc(10)),
    ).rejects.toBeInstanceOf(BadRequestException);
    await service.putChunk('u1', 'c1', s.uploadId, 0, Buffer.alloc(CHUNK, 1));
    const afterOne = await service.putChunk('u1', 'c1', s.uploadId, 2, Buffer.alloc(100, 3));
    expect(afterOne.received).toEqual([0, 2]);
    await expect(service.complete('u1', 'c1', s.uploadId)).rejects.toBeInstanceOf(
      BadRequestException,
    );

    await service.putChunk('u1', 'c1', s.uploadId, 1, Buffer.alloc(CHUNK, 2));
    upload.mockResolvedValue({ id: 'att-1' });
    await service.complete('u1', 'c1', s.uploadId);
    const arg = upload.mock.calls[0][0];
    expect(arg.buffer.length).toBe(total);
    expect(arg.mimeType).toBe('video/mp4');
    // staging is removed after completion
    await expect(service.status('u1', 'c1', s.uploadId)).rejects.toBeInstanceOf(NotFoundException);
  });

  it('hides other users uploads behind 404', async () => {
    const s = await service.init({ ...base, totalSize: CHUNK, chunkSize: CHUNK });
    await expect(service.status('intruder', 'c1', s.uploadId)).rejects.toBeInstanceOf(
      NotFoundException,
    );
    await expect(service.status('u1', 'other-conversation', s.uploadId)).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});
