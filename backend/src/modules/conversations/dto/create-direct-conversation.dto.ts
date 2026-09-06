import { IsUUID } from 'class-validator';

export class CreateDirectConversationDto {
  @IsUUID()
  targetUserId!: string;
}
