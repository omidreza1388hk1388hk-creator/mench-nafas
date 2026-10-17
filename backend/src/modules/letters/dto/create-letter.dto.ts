import { IsISO8601, IsOptional, IsString, IsUUID, MaxLength, MinLength } from 'class-validator';

export class CreateLetterDto {
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  title!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(5000)
  body!: string;

  /** Optional photo: an image uploaded to this conversation; only shown to the recipient once the letter is opened. */
  @IsOptional()
  @IsUUID()
  attachmentId?: string;

  /** Optional: the recipient cannot open the letter before this instant. */
  @IsOptional()
  @IsISO8601()
  unlockAt?: string;
}
