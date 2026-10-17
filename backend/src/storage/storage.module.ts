import { Global, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { LocalDiskStorageProvider } from './local-disk-storage.provider';
import { S3StorageProvider } from './s3-storage.provider';
import { StorageProvider } from './storage-provider.interface';

export const STORAGE_PROVIDER = 'STORAGE_PROVIDER';

@Global()
@Module({
  providers: [
    LocalDiskStorageProvider,
    S3StorageProvider,
    {
      // STORAGE_PROVIDER=s3 (required by env.validation.ts whenever
      // NODE_ENV=production) now resolves to a real S3-compatible
      // provider — see s3-storage.provider.ts. Same pattern as
      // modules/auth/auth.module.ts's SMS_PROVIDER.
      provide: STORAGE_PROVIDER,
      inject: [ConfigService, LocalDiskStorageProvider, S3StorageProvider],
      useFactory: (
        config: ConfigService,
        local: LocalDiskStorageProvider,
        s3: S3StorageProvider,
      ): StorageProvider => {
        const provider = config.get<string>('STORAGE_PROVIDER');
        if (provider === 'local') {
          return local;
        }
        if (provider === 's3') {
          return s3;
        }
        throw new Error(`STORAGE_PROVIDER="${provider}" has no implementation.`);
      },
    },
  ],
  exports: [STORAGE_PROVIDER],
})
export class StorageModule {}
