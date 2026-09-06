import { NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { mkdtempSync, rmSync, writeFileSync, existsSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { LocalDiskStorageProvider } from './local-disk-storage.provider';

describe('LocalDiskStorageProvider', () => {
  let workDir: string;
  let baseDir: string;
  let provider: LocalDiskStorageProvider;

  beforeEach(() => {
    // baseDir is a SUBDIRECTORY of workDir, not workDir itself, so there's
    // a real sibling directory/file outside baseDir to attempt to escape
    // to — a traversal test against a base dir with no meaningful parent
    // content wouldn't actually prove the guard does anything.
    workDir = mkdtempSync(join(tmpdir(), 'mench-storage-test-'));
    baseDir = join(workDir, 'uploads');
    const config = new ConfigService({ STORAGE_LOCAL_DIR: baseDir });
    provider = new LocalDiskStorageProvider(config);
  });

  afterEach(() => {
    rmSync(workDir, { recursive: true, force: true });
  });

  it('writes and reads back the exact bytes for a normal key', async () => {
    const data = Buffer.from('hello mench');
    await provider.put('some-file.txt', data, 'text/plain');

    const read = await provider.get('some-file.txt');

    expect(read.equals(data)).toBe(true);
  });

  it('refuses to read a real file that exists just outside the base directory via a traversal key', async () => {
    // A real, existing, readable file sitting one level above baseDir —
    // if the guard is doing nothing, this read would succeed and leak it.
    const secretPath = join(workDir, 'secret.txt');
    writeFileSync(secretPath, 'this must never be reachable via provider.get()');
    expect(existsSync(secretPath)).toBe(true);

    await expect(provider.get('../secret.txt')).rejects.toBeInstanceOf(NotFoundException);
  });

  it('refuses a deeply nested traversal attempt the same way', async () => {
    await expect(provider.get('../../../../../../etc/passwd')).rejects.toBeInstanceOf(NotFoundException);
  });

  it('deleting a nonexistent key does not throw', async () => {
    await expect(provider.delete('never-existed.txt')).resolves.not.toThrow();
  });

  it('getting a nonexistent (but validly-pathed) key throws NotFoundException, not a raw fs error', async () => {
    await expect(provider.get('nonexistent.txt')).rejects.toBeInstanceOf(NotFoundException);
  });
});
