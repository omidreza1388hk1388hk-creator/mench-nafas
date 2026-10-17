import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand,
  NoSuchKey,
} from '@aws-sdk/client-s3';
import { Readable } from 'stream';
import { StorageProvider, StoredObjectMeta } from './storage-provider.interface';

/**
 * Object-storage-backed StorageProvider, speaking the plain S3 API — works
 * against real AWS S3, Cloudflare R2, Backblaze B2, or a self-hosted MinIO,
 * since all four implement the same PutObject/GetObject/DeleteObject
 * surface. This is the swap point storage.module.ts's factory already
 * reserves behind STORAGE_PROVIDER=s3, using the S3_ENDPOINT/S3_ACCESS_KEY/
 * S3_SECRET_KEY/S3_BUCKET env vars env.validation.ts already validates.
 *
 * Recommended for this project: Cloudflare R2 (free tier: 10GB storage, no
 * egress fees — matters because attachments are only ever fetched by the
 * two people in this messenger, but R2's zero egress cost removes any
 * reason to think about it). S3_ENDPOINT for R2 is
 * https://<account-id>.r2.cloudflarestorage.com, S3_REGION can stay "auto".
 */
@Injectable()
export class S3StorageProvider implements StorageProvider {
  private readonly logger = new Logger('S3StorageProvider');
  // Built lazily (see ensureClient()), not in the constructor: this class
  // is registered unconditionally in storage.module.ts's providers array
  // so Nest's DI container instantiates it on every boot, including a
  // plain STORAGE_PROVIDER=local dev run where S3_* is blank on purpose —
  // throwing from the constructor would break local dev even when this
  // provider is never actually selected by the STORAGE_PROVIDER factory.
  private client: S3Client | null = null;
  private bucket: string | null = null;

  constructor(private readonly config: ConfigService) {}

  async put(key: string, data: Buffer, contentType: string): Promise<StoredObjectMeta> {
    const { client, bucket } = this.ensureClient();
    await client.send(
      new PutObjectCommand({ Bucket: bucket, Key: key, Body: data, ContentType: contentType }),
    );
    this.logger.debug(`Stored ${key} (${data.length} bytes, ${contentType}) in bucket ${bucket}`);
    return { key, contentType, sizeBytes: data.length };
  }

  async get(key: string): Promise<Buffer> {
    const { client, bucket } = this.ensureClient();
    try {
      const result = await client.send(new GetObjectCommand({ Bucket: bucket, Key: key }));
      return this.streamToBuffer(result.Body as Readable);
    } catch (err) {
      if (err instanceof NoSuchKey) {
        throw new NotFoundException('Attachment content not found');
      }
      throw err;
    }
  }

  async delete(key: string): Promise<void> {
    const { client, bucket } = this.ensureClient();
    // DeleteObjectCommand does not error on a missing key (S3 semantics
    // treat delete-of-nonexistent as a no-op success) — same behavior
    // LocalDiskStorageProvider.delete gives with { force: true }, kept
    // consistent across both providers so callers never need to branch.
    await client.send(new DeleteObjectCommand({ Bucket: bucket, Key: key }));
  }

  /** Builds (once) and returns the S3 client + bucket, or throws a clear error if misconfigured. */
  private ensureClient(): { client: S3Client; bucket: string } {
    if (this.client && this.bucket) {
      return { client: this.client, bucket: this.bucket };
    }

    const endpoint = this.config.get<string>('S3_ENDPOINT');
    const accessKeyId = this.config.get<string>('S3_ACCESS_KEY');
    const secretAccessKey = this.config.get<string>('S3_SECRET_KEY');
    const bucket = this.config.get<string>('S3_BUCKET');
    const region = this.config.get<string>('S3_REGION') || 'auto';

    if (!endpoint || !accessKeyId || !secretAccessKey || !bucket) {
      throw new Error(
        'STORAGE_PROVIDER=s3 requires S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY and S3_BUCKET to all be set.',
      );
    }

    const client = new S3Client({
      endpoint,
      region,
      credentials: { accessKeyId, secretAccessKey },
      // R2/MinIO both need path-style addressing (bucket in the URL path,
      // not as a subdomain) — real AWS S3 tolerates this too, so it's
      // safe to always set rather than branching on provider.
      forcePathStyle: true,
    });
    this.bucket = bucket;
    this.client = client;
    return { client, bucket };
  }

  private async streamToBuffer(stream: Readable): Promise<Buffer> {
    const chunks: Buffer[] = [];
    for await (const chunk of stream) {
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
    }
    return Buffer.concat(chunks);
  }
}
