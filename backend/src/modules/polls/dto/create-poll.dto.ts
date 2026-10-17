import { ArrayMaxSize, ArrayMinSize, IsArray, IsBoolean, IsOptional, IsString, Length } from 'class-validator';

export class CreatePollDto {
  @IsString()
  @Length(1, 200)
  question!: string;

  @IsArray()
  @ArrayMinSize(2)
  @ArrayMaxSize(10)
  @IsString({ each: true })
  @Length(1, 100, { each: true })
  options!: string[];

  @IsOptional()
  @IsBoolean()
  multiple?: boolean;
}
