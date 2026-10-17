import { Equals, IsString } from 'class-validator';

/** Deleting an account is irreversible, so the client must send the literal word — a stray request can't trigger it. */
export class DeleteAccountDto {
  @IsString()
  @Equals('DELETE')
  confirm!: string;
}
