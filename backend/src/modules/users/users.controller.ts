import { Body, Controller, Get, NotFoundException, Param, ParseUUIDPipe, Patch, Post, Put, Query, Req, Res, UploadedFile, UseGuards, BadRequestException } from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
import { Response } from 'express';
import { UseInterceptors } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { UsersService } from './users.service';
import { LookupUserQueryDto } from './dto/lookup-user-query.dto';
import { LookupUsersBatchDto } from './dto/lookup-users-batch.dto';
import { UpdateProfileDto } from './dto/update-profile.dto';
import { SetE2eeKeyDto } from './dto/set-e2ee-key.dto';
import { UpdateNotificationPrivacyDto } from './dto/update-notification-privacy.dto';
import { UpdateContactPrivacyDto } from './dto/update-contact-privacy.dto';
import { toUserDto } from './user.dto';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { PrivacyService } from '../privacy/privacy.service';

@UseGuards(JwtAuthGuard)
@Controller('users')
export class UsersController {
  constructor(
    private readonly usersService: UsersService,
    private readonly privacy: PrivacyService,
  ) {}

  /**
   * The caller's own profile, resolved from the access token's subject
   * claim rather than any client-supplied id — a user can only ever read
   * their own record through this route (spec section 34: never trust
   * client-supplied identity for what "me" means).
   */
  @Get('me')
  async getMe(@Req() req: any) {
    const user = await this.usersService.findById(req.user.sub);
    if (!user) {
      throw new NotFoundException('User not found');
    }
    return toUserDto(user);
  }

  /** Upload/replace the caller's profile photo (multipart field "file", max 8 MB; server resizes to 256x256). */
  @Post('me/avatar')
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @UseInterceptors(FileInterceptor('file', { storage: memoryStorage(), limits: { fileSize: 8 * 1024 * 1024 } }))
  async uploadAvatar(@Req() req: any, @UploadedFile() file?: Express.Multer.File) {
    if (!file) throw new BadRequestException('file is required');
    const user = await this.usersService.setAvatar(req.user.sub, file.buffer);
    return toUserDto(user);
  }

  /** Find someone by @username (public fields only — never the phone number). */
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @Get('by-username/:username')
  async getByUsername(@Req() req: any, @Param('username') username: string) {
    const found = await this.usersService.findByUsernameOrThrow(req.user.sub, username);
    await this.privacy.assertDiscoverable(found.id, req.user.sub); // "who can find me" (Phase 4C)
    return toPublicUserDto(found);
  }

  /** The caller's own invite code; the app builds mench://invite/<code> and the QR from it. */
  @Get('me/invite')
  async getMyInvite(@Req() req: any) {
    return { code: await this.usersService.getInviteCode(req.user.sub) };
  }

  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @Get('invite/:code')
  async resolveInvite(@Req() req: any, @Param('code') code: string) {
    const found = await this.usersService.resolveInviteCode(req.user.sub, code);
    await this.privacy.assertDiscoverable(found.id, req.user.sub);
    return toPublicUserDto(found);
  }

  @Get('me/blocks')
  async listBlocked(@Req() req: any) {
    return (await this.usersService.listBlocked(req.user.sub)).map(toPublicUserDto);
  }

  /** Full JSON export of what the user authored — offered before deleting the account. */
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @Get('me/export')
  async exportMyData(@Req() req: any) {
    return this.usersService.exportData(req.user.sub);
  }

  /** Irreversible: anonymises the account and signs out every device. Body must be {"confirm":"DELETE"}. */
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @Delete('me')
  @HttpCode(200)
  async deleteMe(@Req() req: any, @Body() _dto: DeleteAccountDto) {
    await this.usersService.deleteAccount(req.user.sub);
    return { ok: true };
  }

  @Post(':userId/block')
  @HttpCode(200)
  async block(@Req() req: any, @Param('userId', ParseUUIDPipe) userId: string) {
    await this.usersService.blockUser(req.user.sub, userId);
    return { ok: true };
  }

  @Delete(':userId/block')
  @HttpCode(200)
  async unblock(@Req() req: any, @Param('userId', ParseUUIDPipe) userId: string) {
    await this.usersService.unblockUser(req.user.sub, userId);
    return { ok: true };
  }

  /** Phase 4C: publish my end-to-end-encryption public key. */
  @Put('me/e2ee-key')
  async setE2eeKey(@Req() req: any, @Body() dto: SetE2eeKeyDto) {
    await this.usersService.setE2eeKey(req.user.sub, dto.publicKey);
    return { ok: true };
  }

  @Get(':userId/e2ee-key')
  async getE2eeKey(@Req() req: any, @Param('userId', ParseUUIDPipe) userId: string) {
    return { publicKey: await this.usersService.getE2eeKey(req.user.sub, userId) };
  }

  /** Profile photo of yourself or of someone you share a conversation with. Version in ?v= is only a cache-buster. */
  @Get(':userId/avatar')
  async getAvatar(
    @Req() req: any,
    @Param('userId', ParseUUIDPipe) userId: string,
    @Res() res: Response,
  ): Promise<void> {
    await this.privacy.assertPhotoVisible(userId, req.user.sub); // profile-photo visibility (Phase 4C)
    const buffer = await this.usersService.getAvatar(req.user.sub, userId);
    res.set({
      'Content-Type': 'image/jpeg',
      'Content-Length': buffer.length.toString(),
      'Cache-Control': 'private, max-age=31536000, immutable',
    });
    res.send(buffer);
  }

  /** Same identity rule as getMe: the row updated is always req.user.sub, never a body-supplied id. */
  @Patch('me')
  async updateMe(@Req() req: any, @Body() dto: UpdateProfileDto) {
    const user = await this.usersService.updateProfile(req.user.sub, dto);
    return toUserDto(user);
  }

  /**
   * Returns just the current mode — not a full "my profile" endpoint,
   * since the Android settings screen needs nothing else here. See
   * updateNotificationPrivacy below for why this one field is split out
   * from the general PATCH /users/me above rather than folded into it.
   */
  @Get('me/notification-privacy')
  async getNotificationPrivacy(@Req() req: any) {
    const user = await this.usersService.findById(req.user.sub);
    if (!user) {
      // Should be unreachable — a valid JWT implies the user row exists —
      // but NotFoundException here is still safer than a null-pointer
      // 500 if a user was ever deleted out from under a still-valid token.
      throw new NotFoundException('User not found');
    }
    return { mode: user.notification_privacy_mode };
  }

  /**
   * The one notification setting that exists today (master-prompt
   * section 30's three privacy modes) — kept as its own endpoint rather
   * than a field on UpdateProfileDto because NotificationsService reads
   * it on every single push send and the two are conceptually unrelated
   * (display identity vs. notification behavior), not because of any
   * technical constraint.
   */
  @Patch('me/notification-privacy')
  async updateNotificationPrivacy(@Req() req: any, @Body() dto: UpdateNotificationPrivacyDto) {
    await this.usersService.updateNotificationPrivacyMode(req.user.sub, dto.mode);
    return { ok: true };
  }

  /** Contact-sync discoverability (Phase 1 completion). Always about the caller, never a body-supplied id. */
  @Get('me/contact-privacy')
  async getContactPrivacy(@Req() req: any) {
    return { discoverable: await this.usersService.getContactPrivacy(req.user.sub) };
  }

  @Patch('me/contact-privacy')
  async updateContactPrivacy(@Req() req: any, @Body() dto: UpdateContactPrivacyDto) {
    await this.usersService.setContactPrivacy(req.user.sub, dto.discoverable);
    return { discoverable: dto.discoverable };
  }

  /**
   * Resolves a phone number to a user id, so the Android client can start
   * a conversation "by phone number" (per Phase 2's chosen UX) while the
   * actual conversation-creation endpoint still deals only in user ids —
   * phone numbers are never used as a foreign key anywhere past this
   * lookup. Rate-limited like the OTP endpoints: this is the one place in
   * the API that turns "do you have an account" into a yes/no oracle for
   * an arbitrary phone number, and that enumeration surface should be
   * throttled the same way, not left open.
   */
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @Get('lookup')
  async lookupByPhone(@Query() query: LookupUserQueryDto) {
    const user = await this.usersService.findByPhone(query.phone);
    if (!user) {
      throw new NotFoundException('No user found for that phone number');
    }
    return toUserDto(user);
  }

  /**
   * Contact sync: the client sends raw E.164 numbers from the device
   * address book — same trust model as GET lookup above, just batched.
   * Silently returns only the matches (never "not found" for the rest),
   * unlike the single-lookup endpoint's 404 — a contacts screen needs to
   * keep going and simply omit the numbers with no account, not fail
   * the whole sync over the (overwhelmingly common) case of some
   * contacts not being on MENCH. Throttled harder per call (5/min, not
   * 10) since each call can enumerate up to 500 numbers at once rather
   * than one.
   */
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('lookup-batch')
  async lookupBatch(@Body() dto: LookupUsersBatchDto) {
    const users = await this.usersService.findManyByPhones(dto.phones);
    return users.map(toUserDto);
  }
}
