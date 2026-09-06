import { ArrayMaxSize, ArrayMinSize, ArrayUnique, IsUUID } from 'class-validator';

export class AddMembersDto {
  @IsUUID(undefined, { each: true })
  @ArrayMinSize(1)
  @ArrayMaxSize(255)
  @ArrayUnique()
  memberIds!: string[];
}
