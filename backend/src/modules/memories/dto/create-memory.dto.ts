import { IsOptional, IsString, IsUUID, Matches, MaxLength, MinLength } from 'class-validator';

export class CreateMemoryDto {
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  title!: string;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  note?: string;

  /** The day the memory is about, YYYY-MM-DD. */
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  memoryDate!: string;

  /** Optional photo: id of an image attachment already uploaded to this conversation. */
  @IsOptional()
  @IsUUID()
  attachmentId?: string;
}
