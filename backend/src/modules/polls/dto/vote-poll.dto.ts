import { ArrayMaxSize, IsArray, IsUUID } from 'class-validator';

export class VotePollDto {
  /** The complete set of options this user now votes for. An empty array retracts the vote. */
  @IsArray()
  @ArrayMaxSize(10)
  @IsUUID('all', { each: true })
  optionIds!: string[];
}
