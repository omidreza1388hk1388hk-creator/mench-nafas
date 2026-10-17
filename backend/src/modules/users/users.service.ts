import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { BadRequestException, Inject, ForbiddenException } from '@nestjs/common';
import sharp from 'sharp';
import { UsersRepository, UserRow, NotificationPrivacyMode } from './users.repository';
import { STORAGE_PROVIDER } from '../../storage/storage.module';
import { StorageProvider } from '../../storage/storage-provider.interface';

@Injectable()
export class UsersService {
  constructor(
    private readonly usersRepository: UsersRepository,
    @Inject(STORAGE_PROVIDER) private readonly storage: StorageProvider,
  ) {}

  /** Square-crops to 256x256 JPEG (EXIF-rotated, metadata stripped), stores it, bumps avatar_version. */
  async setAvatar(userId: string, image: Buffer): Promise<UserRow> {
    let processed: Buffer;
    try {
      processed = await sharp(image)
        .rotate()
        .resize(256, 256, { fit: 'cover' })
        .jpeg({ quality: 82 })
        .toBuffer();
    } catch {
      throw new BadRequestException('Not a valid image');
    }
    await this.storage.put(`avatars/${userId}.jpg`, processed, 'image/jpeg');
    await this.usersRepository.setAvatarVersion(userId, Date.now());
    const user = await this.usersRepository.findById(userId);
    if (!user) throw new NotFoundException('User not found');
    return user;
  }

  /** Only the user themself or someone sharing a conversation with them may fetch a photo. */
  async getAvatar(requesterId: string, targetUserId: string): Promise<Buffer> {
    if (!(await this.usersRepository.sharesConversation(requesterId, targetUserId))) {
      throw new NotFoundException('Avatar not found');
    }
    const user = await this.usersRepository.findById(targetUserId);
    if (!user?.avatar_version) throw new NotFoundException('Avatar not found');
    return this.storage.get(`avatars/${targetUserId}.jpg`);
  }

  /** Phase 4C: publish the caller's E2EE public key (replaces the previous one). */
  async setE2eeKey(userId: string, publicKey: string): Promise<void> {
    await this.usersRepository.setE2eeKey(userId, publicKey);
  }

  /** Phase 4C: a key is only handed out to yourself or someone you share a conversation with. */
  async getE2eeKey(requesterId: string, targetUserId: string): Promise<string | null> {
    if (
      requesterId !== targetUserId &&
      !(await this.usersRepository.sharesConversation(requesterId, targetUserId))
    ) {
      throw new NotFoundException('User not found');
    }
    return this.usersRepository.getE2eeKey(targetUserId);
  }

  findOrCreateByPhone(phoneE164: string): Promise<UserRow> {
    return this.usersRepository.findOrCreateByPhone(phoneE164);
  }

  /**
   * Read-only lookup, deliberately separate from findOrCreateByPhone:
   * looking up a phone number to start a conversation must never have the
   * side effect of creating an account for someone who was just typed in
   * by another user — findOrCreateByPhone is reserved for the OTP-verified
   * owner of that number authenticating themselves.
   */
  findByPhone(phoneE164: string): Promise<UserRow | null> {
    return this.usersRepository.findByPhone(phoneE164);
  }

  findById(id: string): Promise<UserRow | null> {
    return this.usersRepository.findById(id);
  }

  findManyByIds(ids: string[]): Promise<UserRow[]> {
    return this.usersRepository.findManyByIds(ids);
  }

  findManyByPhones(phones: string[]): Promise<UserRow[]> {
    return this.usersRepository.findManyByPhones(phones);
  }

  /**
   * The 23505 catch is deliberately narrow: it only turns a
   * unique-violation on the username column into a client-facing 409.
   * Any other database error (connection loss, a different constraint)
   * still propagates as an unhandled 500 rather than being misreported
   * as "username taken".
   */
  async updateProfile(
    userId: string,
    patch: { displayName?: string; username?: string; bio?: string },
  ): Promise<UserRow> {
    const existing = await this.usersRepository.findById(userId);
    if (!existing) {
      throw new NotFoundException('User not found');
    }

    try {
      return await this.usersRepository.updateProfile(userId, patch);
    } catch (err) {
      if ((err as { code?: string }).code === '23505') {
        throw new ConflictException('That username is already taken');
      }
      throw err;
    }
  }

  getContactPrivacy(userId: string): Promise<boolean> {
    return this.usersRepository.getContactPrivacy(userId);
  }

  setContactPrivacy(userId: string, discoverable: boolean): Promise<void> {
    return this.usersRepository.setContactPrivacy(userId, discoverable);
  }

  updateNotificationPrivacyMode(userId: string, mode: NotificationPrivacyMode): Promise<void> {
    return this.usersRepository.updateNotificationPrivacyMode(userId, mode);
  }
}
