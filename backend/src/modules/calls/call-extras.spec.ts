import { CALL_REACTIONS, isAllowedCallReaction, MAX_CALL_CHAT_LENGTH, sanitizeCallChatText } from './call-extras';

describe('call extras validation', () => {
  it('trims and caps chat text, strips control characters, rejects empty and non-strings', () => {
    expect(sanitizeCallChatText('  hi  ')).toBe('hi');
    expect(sanitizeCallChatText('a\u0000b\u0007c')).toBe('abc');
    expect(sanitizeCallChatText('line1\nline2')).toBe('line1\nline2');
    expect(sanitizeCallChatText('x'.repeat(MAX_CALL_CHAT_LENGTH + 50))).toHaveLength(MAX_CALL_CHAT_LENGTH);
    expect(sanitizeCallChatText('   ')).toBeNull();
    expect(sanitizeCallChatText(42)).toBeNull();
    expect(sanitizeCallChatText(undefined)).toBeNull();
  });

  it('only allows the offered reaction emoji', () => {
    for (const e of CALL_REACTIONS) expect(isAllowedCallReaction(e)).toBe(true);
    expect(isAllowedCallReaction('💩')).toBe(false);
    expect(isAllowedCallReaction('hello')).toBe(false);
    expect(isAllowedCallReaction(null)).toBe(false);
  });
});
