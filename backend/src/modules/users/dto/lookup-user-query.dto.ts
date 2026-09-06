import { IsPhoneNumber } from 'class-validator';

export class LookupUserQueryDto {
  @IsPhoneNumber(undefined, { message: 'phone must be a valid E.164 phone number' })
  phone!: string;
}
