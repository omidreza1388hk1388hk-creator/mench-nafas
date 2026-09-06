import { IsUUID } from 'class-validator';

export class ForwardMessageDto {
  @IsUUID()
  sourceMessageId!: string;

  @IsUUID()
  clientMsgId!: string;
}
