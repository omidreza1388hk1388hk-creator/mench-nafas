import { IsString, Length } from 'class-validator';

/**
 * emoji is validated only for length, not against a fixed allowlist —
 * the client currently offers a small preset row, but the server has no
 * reason to hardcode that same list and reject anything outside it
 * (custom/animated MENCH emoji from spec section 20 will need to send
 * something other than a raw Unicode grapheme here eventually).
 */
export class ReactMessageDto {
  @IsString()
  @Length(1, 32)
  emoji!: string;
}
