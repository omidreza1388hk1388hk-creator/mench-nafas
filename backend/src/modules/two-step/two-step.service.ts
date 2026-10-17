import { BadRequestException, HttpException, HttpStatus, Injectable, UnauthorizedException } from '@nestjs/common';
import * as argon2 from 'argon2';
import { TwoStepRepository } from './two-step.repository';
import {
  MAX_TICKET_ATTEMPTS,
  TICKET_TTL_MINUTES,
  isLocked,
  lockUntilAfterFailure,
  passwordProblem,
} from './two-step.policy';

export interface TwoStepChallenge {
  twoStepRequired: true;
  ticket: string;
  hint: string | null;
}

@Injectable()
export class TwoStepService {
  constructor(private readonly repository: TwoStepRepository) {}

  async status(userId: string): Promise<{ enabled: boolean; hint: string | null }> {
    const row = await this.repository.get(userId);
    return { enabled: !!row, hint: row?.hint ?? null };
  }

  async isEnabled(userId: string): Promise<boolean> {
    return !!(await this.repository.get(userId));
  }

  /** Turning it on, or changing the password. Changing requires the current password. */
  async set(userId: string, password: string, hint: string | undefined, currentPassword: string | undefined): Promise<void> {
    const problem = passwordProblem(password);
    if (problem) throw new BadRequestException(problem);
    if (hint && hint.toLowerCase().includes(password.toLowerCase())) {
      throw new BadRequestException('The hint must not contain the password');
    }
    if (await this.isEnabled(userId)) {
      await this.assertPassword(userId, currentPassword ?? '');
    }
    await this.repository.upsert(userId, await argon2.hash(password), hint?.trim() || null);
  }

  async disable(userId: string, password: string): Promise<void> {
    if (!(await this.isEnabled(userId))) return;
    await this.assertPassword(userId, password);
    await this.repository.remove(userId);
  }

  /** Called by AuthService after a correct SMS code when the account has a password. */
  async startChallenge(userId: string, deviceName: string): Promise<TwoStepChallenge> {
    const expires = new Date(Date.now() + TICKET_TTL_MINUTES * 60_000);
    const ticket = await this.repository.createTicket(userId, deviceName, expires);
    const row = await this.repository.get(userId);
    return { twoStepRequired: true, ticket, hint: row?.hint ?? null };
  }

  /** Validates ticket + password. Returns who is logging in; the caller then issues the session. */
  async verifyTicket(ticketId: string, password: string): Promise<{ userId: string; deviceName: string }> {
    const ticket = await this.repository.getTicket(ticketId);
    if (!ticket || ticket.expires_at.getTime() < Date.now() || ticket.attempts >= MAX_TICKET_ATTEMPTS) {
      throw new UnauthorizedException('Verification expired — request a new code');
    }
    try {
      await this.assertPassword(ticket.user_id, password);
    } catch (err) {
      await this.repository.bumpTicket(ticket.id);
      throw err;
    }
    await this.repository.deleteTicket(ticket.id);
    return { userId: ticket.user_id, deviceName: ticket.device_name };
  }

  private async assertPassword(userId: string, password: string): Promise<void> {
    const row = await this.repository.get(userId);
    if (!row) throw new UnauthorizedException('Wrong password');
    const now = new Date();
    if (isLocked(row.locked_until, now)) {
      throw new HttpException('Too many wrong passwords. Try again later.', HttpStatus.TOO_MANY_REQUESTS);
    }
    const ok = password.length > 0 && (await argon2.verify(row.password_hash, password));
    if (!ok) {
      await this.repository.recordFailure(userId, (attempts) => lockUntilAfterFailure(attempts, new Date()));
      throw new UnauthorizedException('Wrong password');
    }
    await this.repository.recordSuccess(userId);
  }
}
