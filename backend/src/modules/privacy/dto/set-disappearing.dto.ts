import { IsInt, IsOptional, Max, Min } from 'class-validator';

/** seconds: 0 or omitted = off. Range is re-checked by normalizeDisappearSeconds. */
export class SetDisappearingDto {
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(31_536_000)
  seconds?: number;
}
