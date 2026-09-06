import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { mkdir, readFile, rm, stat, writeFile } from 'fs/promises';
import { join, normalize, resolve, sep } from 'path';
import { StorageProvider, StoredObjectMeta } from './storage-provider.interface';

/**
 * Stores attachment bytes on local disk under STORAGE_LOCAL_DIR. Safe for
 * development and CI; NOT what a real multi-instance production
 * deployment should use (a restarted/rescaled container would lose
 * everything written here) — see docs/ARCHITECTURE.md for the S3 swap
 * point. Never used in production by configuration alone: see
 * attachments.module.ts's provider factory, which mirrors
 * auth.module.ts's OTP_PROVIDER=dev guard.
 */
@Injectable()
export class LocalDiskStorageProvider implements StorageProvider {
  private readonly logger = new Logger('LocalDiskStorageProvider');
  private readonly baseDir: string;

  constructor(config: ConfigService) {
    this.baseDir = resolve(config.get<string>('STORAGE_LOCAL_DIR') ?? './uploads');
  }

  async put(key: string, data: Buffer, contentType: string): Promise<StoredObjectMeta> {
    const path = this.resolveSafePath(key);
    await mkdir(join(path, '..'), { recursive: true });
    await writeFile(path, data);
    this.logger.debug(`Stored ${key} (${data.length} bytes, ${contentType})`);
    return { key, contentType, sizeBytes: data.length };
  }

  async get(key: string): Promise<Buffer> {
    const path = this.resolveSafePath(key);
    try {
      await stat(path);
    } catch {
      throw new NotFoundException('Attachment content not found');
    }
    return readFile(path);
  }

  async delete(key: string): Promise<void> {
    const path = this.resolveSafePath(key);
    await rm(path, { force: true });
  }

  /**
   * Resolves key -> absolute path and verifies the result is still inside
   * baseDir. Keys are server-generated UUIDs (see AttachmentsService), but
   * this check exists so a bug or future change that ever accepts a
   * caller-influenced key can't be turned into path traversal
   * (`../../etc/passwd`-style) — defense in depth, not trust in the input.
   */
  private resolveSafePath(key: string): string {
    const path = resolve(join(this.baseDir, normalize(key)));
    if (!path.startsWith(this.baseDir + sep) && path !== this.baseDir) {
      throw new NotFoundException('Invalid attachment key');
    }
    return path;
  }
}
