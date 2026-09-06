import { IsString, Length } from 'class-validator';

export class RenameGroupDto {
  @IsString()
  @Length(1, 100)
  title!: string;
}
