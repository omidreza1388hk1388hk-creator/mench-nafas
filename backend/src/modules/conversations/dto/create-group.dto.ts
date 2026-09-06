import { ArrayMaxSize, ArrayMinSize, ArrayUnique, IsString, IsUUID, Length } from 'class-validator';

export class CreateGroupDto {
  @IsString()
  @Length(1, 100)
  title!: string;

  /**
   * Just the OTHER members — the creator is added as 'owner' separately
   * (see ConversationsService.createGroup) and must not appear in this
   * list. 1..255 rather than unbounded: a group needs at least one other
   * member to mean anything, and this schema has no per-conversation
   * member count limit elsewhere, so an explicit cap here is the only
   * thing stopping a single malformed request from creating an
   * absurdly large group.
   */
  @IsUUID(undefined, { each: true })
  @ArrayMinSize(1)
  @ArrayMaxSize(255)
  @ArrayUnique()
  memberIds!: string[];
}
