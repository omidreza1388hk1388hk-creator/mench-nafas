import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { UsersRepository, UserRow, NotificationPrivacyMode } from './users.repository';

@Injectable()
export class UsersService {
  constructor(private readonly usersRepository: UsersRepository) {}

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

  updateNotificationPrivacyMode(userId: string, mode: NotificationPrivacyMode): Promise<void> {
    return this.usersRepository.updateNotificationPrivacyMode(userId, mode);
  }
}
