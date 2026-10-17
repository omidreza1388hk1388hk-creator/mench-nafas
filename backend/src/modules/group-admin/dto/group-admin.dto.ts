import { IsIn, IsInt, IsOptional, IsString, IsUUID, Max, Min } from 'class-validator';

export class UpdateGroupSettingsDto {
  @IsOptional() @IsIn(['all', 'admins']) sendMessages?: string;
  @IsOptional() @IsIn(['all', 'admins']) addMembers?: string;
  @IsOptional() @IsIn(['all', 'admins']) editInfo?: string;
  @IsOptional() @IsIn(['all', 'admins']) pinMessages?: string;
  @IsOptional() @IsInt() @IsIn([0, 10, 30, 60, 300, 900, 3600]) slowModeSeconds?: number;
}

export class TransferOwnerDto {
  @IsUUID() userId!: string;
}

export class ChangeRoleDto {
  @IsString()
  @IsIn(['admin', 'member'])
  role!: string;
}

export class CreateInviteDto {
  /** Hours until the link stops working; omit for a link that never expires. */
  @IsOptional() @IsInt() @Min(1) @Max(24 * 365) expiresInHours?: number;
  /** How many people may join with it; omit for unlimited. */
  @IsOptional() @IsInt() @Min(1) @Max(10000) maxUses?: number;
}
