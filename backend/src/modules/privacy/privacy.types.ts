import { BadRequestException } from '@nestjs/common';

export type Visibility = 'everybody' | 'nobody';

export interface PrivacySettings {
  lastSeen: Visibility;
  profilePhoto: Visibility;
  addMe: Visibility;
  calls: Visibility;
  readReceipts: boolean;
}

export const DEFAULT_PRIVACY: PrivacySettings = {
  lastSeen: 'everybody',
  profilePhoto: 'everybody',
  addMe: 'everybody',
  calls: 'everybody',
  readReceipts: true,
};

/** Disappearing-message timer limits (seconds): 1 minute .. 1 year. Presets 24h / 7d / 30d are inside. */
export const DISAPPEAR_MIN_SECONDS = 60;
export const DISAPPEAR_MAX_SECONDS = 31_536_000;

/** null / 0 / undefined mean "off". Anything else must be a whole number inside the allowed range. */
export function normalizeDisappearSeconds(value: number | null | undefined): number | null {
  if (value === null || value === undefined || value === 0) return null;
  if (!Number.isInteger(value) || value < DISAPPEAR_MIN_SECONDS || value > DISAPPEAR_MAX_SECONDS) {
    throw new BadRequestException(
      `seconds must be 0 (off) or an integer between ${DISAPPEAR_MIN_SECONDS} and ${DISAPPEAR_MAX_SECONDS}`,
    );
  }
  return value;
}
