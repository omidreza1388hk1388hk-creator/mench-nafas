import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpdateMoodDto {
  /** A single emoji (a few code points at most — ZWJ sequences and skin tones need room). */
  @IsString()
  @MinLength(1)
  @MaxLength(16)
  emoji!: string;

  @IsOptional()
  @IsString()
  @MaxLength(60)
  text?: string;
}
