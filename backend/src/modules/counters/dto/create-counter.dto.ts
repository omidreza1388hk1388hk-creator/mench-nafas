import {
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  Matches,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

export class CreateCounterDto {
  @IsString()
  @MinLength(1)
  @MaxLength(60)
  title!: string;

  @IsOptional()
  @IsString()
  @MaxLength(8)
  emoji?: string;

  @IsIn(['date', 'tally'])
  kind!: 'date' | 'tally';

  /** Card gradient (index into the client's palette list). */
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(5)
  theme?: number;

  /** Required for kind 'date': the day to count from or towards, YYYY-MM-DD. */
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  anchorDate?: string;
}
