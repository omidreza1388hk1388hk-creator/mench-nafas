import { IsString, Matches, MaxLength } from 'class-validator';

/** The server never sees the plaintext: only an opaque "v1:<base64>" blob produced on the phone. */
export class CreateVaultItemDto {
  @IsString()
  @MaxLength(16000)
  @Matches(/^v1:[A-Za-z0-9+/=]{16,}$/)
  blob!: string;
}
