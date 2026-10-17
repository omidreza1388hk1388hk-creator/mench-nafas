/**
 * Stickers and GIFs travel as ordinary text messages whose body is a small versioned marker
 * (so no schema change, and old app versions just show the marker text):
 *   sticker:v1:<id>                              catalog sticker
 *   sticker:v1:c:<palette>:<emoji>:<caption>     custom sticker (emoji + caption percent-encoded)
 *   gif:v1:<giphyId>:<width>:<height>
 * The Android client parses/renders them (RichBody.kt); the server only needs to recognise
 * them so notifications say "[sticker]" / "[GIF]" instead of leaking the marker.
 */
const STICKER_PATTERN = /^sticker:v1:[A-Za-z0-9_%:.-]{1,400}$/;
const GIF_PATTERN = /^gif:v1:[A-Za-z0-9]{5,32}:\d{1,4}:\d{1,4}$/;
const POLL_PATTERN = /^poll:v1:[0-9a-fA-F-]{36}$/;
const SURPRISE_PATTERN = /^surprise:v1:[A-Za-z0-9_%.-]{1,4000}$/;
const E2EE_PATTERN = /^e2ee:v2:[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const CONTACT_PATTERN = /^contact:v1:[A-Za-z0-9_%.-]{1,300}:\+?[0-9%A-Za-z]{1,60}$/;
const LOCATION_PATTERN = /^loc:v1:-?\d{1,3}\.\d{1,7}:-?\d{1,3}\.\d{1,7}$/;

export function describeRichBody(body: string): string | null {
  const trimmed = body.trim();
  if (GIF_PATTERN.test(trimmed)) return '[GIF]';
  if (STICKER_PATTERN.test(trimmed)) return '[sticker]';
  if (POLL_PATTERN.test(trimmed)) return '[poll]';
  if (LOCATION_PATTERN.test(trimmed)) return '[location]';
  if (CONTACT_PATTERN.test(trimmed)) return '[contact]';
  if (SURPRISE_PATTERN.test(trimmed)) return '[surprise]';
  if (E2EE_PATTERN.test(trimmed)) return '[encrypted message]';
  return null;
}
