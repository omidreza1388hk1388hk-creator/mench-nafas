import { Body, Controller, Get, NotFoundException, Patch, Query, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { UsersService } from './users.service';
import { LookupUserQueryDto } from './dto/lookup-user-query.dto';
import { UpdateProfileDto } from './dto/update-profile.dto';
import { UpdateNotificationPrivacyDto } from './dto/update-notification-privacy.dto';
import { toUserDto } from './user.dto';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@UseGuards(JwtAuthGuard)
@Controller('users')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

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
}
