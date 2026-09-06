import { IsPhoneNumber, IsString, IsUUID, Length } from 'class-validator';

export class VerifyOtpDto {
  @IsPhoneNumber(undefined, { message: 'phone must be a valid E.164 phone number' })
  phone!: string;

  @IsString()
  @Length(4, 8)
  code!: string;

  @IsUUID()
  challengeId!: string;

  @IsString()
  @Length(1, 64)
  deviceName!: string;
}
