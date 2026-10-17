import { ArrayMaxSize, ArrayMinSize, IsArray, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateQuizDto {
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  title!: string;

  /** Shape and ranges are validated by sanitizeQuestions in the service. */
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(8)
  questions!: unknown[];
}

export class AnswerQuizDto {
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(8)
  answers!: unknown[];
}
