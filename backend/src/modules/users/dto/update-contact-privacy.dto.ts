import { IsBoolean } from 'class-validator';

/** "Let people who have my number in their contacts find me on MENCH". */
export class UpdateContactPrivacyDto {
  @IsBoolean()
  discoverable!: boolean;
}
