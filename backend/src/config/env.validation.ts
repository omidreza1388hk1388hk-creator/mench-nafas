import * as Joi from 'joi';

/**
 * Fails fast at startup if required configuration is missing or malformed,
 * instead of failing unpredictably later at request time.
 *
 * NOTE: requires the `joi` package (add to package.json dependencies if not
 * already present: "joi": "^17.13.0"). Declared separately from the main
 * dependency list to keep this file's contract explicit.
 */
export const envValidationSchema = Joi.object({
  NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
  PORT: Joi.number().default(3000),

  DATABASE_URL: Joi.string().uri().required(),
  REDIS_URL: Joi.string().uri().required(),

  JWT_ACCESS_SECRET: Joi.string().min(16).required(),
  JWT_REFRESH_SECRET: Joi.string().min(16).required(),
  JWT_ACCESS_TTL_SECONDS: Joi.number().default(900),
  JWT_REFRESH_TTL_SECONDS: Joi.number().default(2_592_000),

  OTP_PROVIDER: Joi.string().valid('dev', 'sms').default('dev'),
  OTP_TTL_SECONDS: Joi.number().default(120),
  OTP_LENGTH: Joi.number().min(4).max(8).default(5),
  OTP_MAX_ATTEMPTS: Joi.number().default(5),
  OTP_RESEND_COOLDOWN_SECONDS: Joi.number().default(45),

  SMS_PROVIDER_API_KEY: Joi.string().allow('').optional(),
  SMS_PROVIDER_SENDER_ID: Joi.string().allow('').optional(),

  STORAGE_PROVIDER: Joi.string().valid('local', 's3').default('local'),
  STORAGE_LOCAL_DIR: Joi.string().default('./uploads'),
  MAX_ATTACHMENT_SIZE_BYTES: Joi.number().default(60 * 1024 * 1024), // 60 MB — bumped from 25MB for video clips
  S3_ENDPOINT: Joi.string().allow('').optional(),
  S3_ACCESS_KEY: Joi.string().allow('').optional(),
  S3_SECRET_KEY: Joi.string().allow('').optional(),
  S3_BUCKET: Joi.string().allow('').optional(),

  // WebRTC ICE servers (Phase 6 — calls). STUN_URLS always has a working
  // default (a public STUN server) so calls between two devices that are
  // both reachable directly or via simple NAT still connect out of the
  // box; TURN is what's actually required for the harder NAT/firewall
  // cases (spec section 29) and has no default; a relayed candidate
  // pair simply isn't offered when it's unset, and IceServersService logs
  // that this is happening once at startup rather than failing calls
  // silently. TURN_URL may be a comma-separated list (e.g. one udp: and
  // one tcp: URL for the same relay) — a single set of TURN_USERNAME/
  // TURN_CREDENTIAL applies to all of them, matching how most managed
  // TURN providers issue credentials.
  STUN_URLS: Joi.string().default('stun:stun.l.google.com:19302'),
  TURN_URL: Joi.string().allow('').optional(),
  TURN_USERNAME: Joi.string().allow('').optional(),
  TURN_CREDENTIAL: Joi.string().allow('').optional(),

  // Phase 6. Deliberately NOT forbidden in production the way
  // OTP_PROVIDER=dev is above: push notifications are a
  // graceful-degradation feature (master-prompt principle #49) — running
  // without real Firebase credentials means notifications are logged
  // instead of delivered, not that the app is broken, so there's no
  // reason to hard-block startup over it the way there is for OTP.
  NOTIFICATIONS_PROVIDER: Joi.string().valid('dev', 'fcm').default('dev'),
  FIREBASE_PROJECT_ID: Joi.string().allow('').optional(),
  FIREBASE_CLIENT_EMAIL: Joi.string().allow('').optional(),
  FIREBASE_PRIVATE_KEY: Joi.string().allow('').optional(),
}).custom((value, helpers) => {
  if (value.NODE_ENV === 'production' && value.OTP_PROVIDER === 'dev') {
    return helpers.error('any.invalid', {
      message: 'OTP_PROVIDER=dev is forbidden when NODE_ENV=production',
    });
  }
  if (value.NODE_ENV === 'production' && value.STORAGE_PROVIDER === 'local') {
    return helpers.error('any.invalid', {
      message:
        'STORAGE_PROVIDER=local is forbidden when NODE_ENV=production ' +
        '(local disk storage does not survive a container restart/rescale)',
    });
  }
  return value;
});
