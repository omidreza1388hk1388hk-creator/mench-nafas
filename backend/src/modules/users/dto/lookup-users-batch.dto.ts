import { ArrayMaxSize, ArrayNotEmpty, IsPhoneNumber, IsString } from 'class-validator';

/**
 * ArrayMaxSize(500) — a generous device address book size, but still a
 * hard ceiling: without one, a client (or a modified one) could submit
 * an arbitrarily large array and turn this into an unbounded IN-list
 * query. 500 comfortably covers real contact lists while keeping the
 * query cheap.
 */
export class LookupUsersBatchDto {
  @ArrayNotEmpty()
  @ArrayMaxSize(500)
  @IsPhoneNumber(undefined, {
    each: true,
    message: 'each phone must be a valid E.164 phone number',
  })
  @IsString({ each: true })
  phones!: string[];
}
