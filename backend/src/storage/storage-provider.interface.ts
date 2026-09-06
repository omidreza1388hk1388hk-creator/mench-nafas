export interface StoredObjectMeta {
  key: string;
  contentType: string;
  sizeBytes: number;
}

/**
 * Boundary between MENCH and wherever attachment bytes actually live.
 * Phase 3a ships one real implementation (LocalDiskStorageProvider) safe
 * for development/CI, and a clearly-failing stub for the production S3
 * path — same "dev vs. real, same interface" pattern as
 * modules/auth/sms-provider.interface.ts. No code outside this file
 * should ever need to know which one is active.
 */
export interface StorageProvider {
  put(key: string, data: Buffer, contentType: string): Promise<StoredObjectMeta>;
  get(key: string): Promise<Buffer>;
  delete(key: string): Promise<void>;
}
