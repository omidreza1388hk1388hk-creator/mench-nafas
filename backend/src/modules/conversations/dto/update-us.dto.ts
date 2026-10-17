import { IsOptional, Matches, ValidateIf } from 'class-validator';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Each field: omit = leave alone, null = clear, 'YYYY-MM-DD' = set. */
export class UpdateUsDto {
  @IsOptional()
  @ValidateIf((_, v) => v !== null)
  @Matches(ISO_DATE)
  togetherSince?: string | null;

  @IsOptional()
  @ValidateIf((_, v) => v !== null)
  @Matches(ISO_DATE)
  nextMeetDate?: string | null;
}
