import { Global, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { LocalDiskStorageProvider } from './local-disk-storage.provider';
import { StorageProvider } from './storage-provider.interface';

export const STORAGE_PROVIDER = 'STORAGE_PROVIDER';

@Global()
@Module({
  providers: [
    LocalDiskStorageProvider,
    {
      // Swap point for a real S3-compatible provider: implement
      // StorageProvider against S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/
      // S3_BUCKET (already validated in env.validation.ts, currently
      // unused by any code — reserved), register it here behind
      // STORAGE_PROVIDER=s3. Nothing outside this factory needs to change.
      // Same pattern as modules/auth/auth.module.ts's SMS_PROVIDER.
      provide: STORAGE_PROVIDER,
      inject: [ConfigService, LocalDiskStorageProvider],
      useFactory: (config: ConfigService, local: LocalDiskStorageProvider): StorageProvider => {
        const provider = config.get<string>('STORAGE_PROVIDER');
        if (provider === 'local') {
          return local;
        }
        throw new Error(
          `STORAGE_PROVIDER="${provider}" has no implementation yet. ` +
            `A real StorageProvider (e.g. S3-compatible) must be added before this can be used.`,
        );
      },
    },
  ],
  exports: [STORAGE_PROVIDER],
})
export class StorageModule {}
