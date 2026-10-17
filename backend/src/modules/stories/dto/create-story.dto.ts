import {
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

export class CreateStoryDto {
  @IsString()
  @MinLength(1)
  @MaxLength(280)
  body!: string;

  @IsOptional()
  @IsString()
  @MaxLength(8)
  emoji?: string;

  /** Optional photo (an image uploaded to this conversation) shown behind the text. */
  @IsOptional()
  @IsUUID()
  attachmentId?: string;

  /** Index of the background palette (the client owns the actual colours). */
  @IsInt()
  @Min(0)
  @Max(5)
  palette!: number;
}
