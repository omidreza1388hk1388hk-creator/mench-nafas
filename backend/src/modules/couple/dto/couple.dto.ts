import { IsBoolean, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class AnswerQuestionDto {
  @IsString()
  @MinLength(1)
  @MaxLength(500)
  answer!: string;
}

export class CreateWishDto {
  @IsString()
  @MinLength(1)
  @MaxLength(100)
  title!: string;
}

export class UpdateWishDto {
  @IsOptional()
  @IsBoolean()
  done?: boolean;
}
