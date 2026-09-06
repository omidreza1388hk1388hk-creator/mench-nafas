import { IsIn, IsString, MinLength } from 'class-validator';

export class RegisterPushTokenDto {
  @IsString()
  @MinLength(16) // real FCM tokens are far longer than this; this just rejects obviously-empty/garbage input early
  token!: string;

  @IsIn(['fcm'])
  provider!: 'fcm';
}
