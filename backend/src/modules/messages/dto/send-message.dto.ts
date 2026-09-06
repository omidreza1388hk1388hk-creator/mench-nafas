import { IsString, IsUUID, Length, ValidateIf } from 'class-validator';

/**
 * body is now optional (an attachment-only message, e.g. a photo with no
 * caption, has nothing to put there) but at least one of body/attachmentId
 * must be present — enforced by the two @ValidateIf-guarded rules below,
 * not left to the service layer to silently accept an empty message.
 */
export class SendMessageDto {
  @IsUUID()
  clientMsgId!: string;

  @ValidateIf((dto: SendMessageDto) => !dto.attachmentId)
  @IsString()
  @Length(1, 4000)
  body?: string;

  @ValidateIf((dto: SendMessageDto) => !dto.body)
  @IsUUID()
  attachmentId?: string;
}
