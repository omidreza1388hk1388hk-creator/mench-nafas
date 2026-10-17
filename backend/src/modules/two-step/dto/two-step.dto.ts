import { IsOptional, IsString, IsUUID, Length } from 'class-validator';

export class SetTwoStepDto {
  @IsString() @Length(6, 64) password!: string;
  @IsOptional() @IsString() @Length(0, 80) hint?: string;
  /** Required when changing an existing password. */
  @IsOptional() @IsString() @Length(0, 64) currentPassword?: string;
}

export class DisableTwoStepDto {
  @IsString() @Length(1, 64) password!: string;
}

export class VerifyTwoStepDto {
  @IsUUID() ticket!: string;
  @IsString() @Length(1, 64) password!: string;
}
