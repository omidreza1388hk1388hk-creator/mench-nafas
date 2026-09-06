/**
 * Boundary between MENCH and whatever real SMS/OTP delivery provider is
 * configured. Phase 1 ships two implementations:
 *   - DevSmsProvider: logs the code server-side, used only when
 *     NODE_ENV !== 'production' (enforced in env.validation.ts).
 *   - (Phase 2+) a real provider implementing this same interface.
 *
 * No code outside this file should ever need to know which provider is
 * active — callers depend only on this interface.
 */
export interface SmsProvider {
  sendOtp(phoneE164: string, code: string): Promise<void>;
}
