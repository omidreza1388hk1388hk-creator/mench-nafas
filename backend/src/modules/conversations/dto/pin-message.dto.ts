import { IsOptional, IsUUID, ValidateIf } from 'class-validator';

export class PinMessageDto {
  /** The message to pin, or null to clear the pin. */
  @IsOptional()
  @ValidateIf((_, v) => v !== null)
  @IsUUID()
  messageId?: string | null;
}
