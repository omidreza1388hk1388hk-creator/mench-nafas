import { IsBoolean, IsISO8601, IsOptional, IsString, IsUUID, Length } from 'class-validator';

export class CreateScheduledMessageDto {
  @IsUUID()
  clientMsgId!: string;

  @IsString()
  @Length(1, 4000)
  body!: string;

  /** ISO-8601 instant. */
  @IsISO8601()
  sendAt!: string;

  @IsOptional()
  @IsBoolean()
  silent?: boolean;
}
