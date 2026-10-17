import { IsBoolean, IsOptional } from 'class-validator';

export class UpdateConversationSettingsDto {
  @IsOptional()
  @IsBoolean()
  isMuted?: boolean;

  @IsOptional()
  @IsBoolean()
  isPinned?: boolean;

  @IsOptional()
  @IsBoolean()
  isArchived?: boolean;
}
