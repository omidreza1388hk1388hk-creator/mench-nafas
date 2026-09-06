import { IsPhoneNumber } from 'class-validator';

export class RequestOtpDto {
  @IsPhoneNumber(undefined, { message: 'phone must be a valid E.164 phone number' })
  phone!: string;
}
