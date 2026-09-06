import { IsIn, IsUUID } from 'class-validator';

export class InitiateCallDto {
  @IsUUID()
  conversationId!: string;

  @IsIn(['voice', 'video'])
  callType!: 'voice' | 'video';
}
