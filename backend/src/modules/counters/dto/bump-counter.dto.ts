import { IsIn } from 'class-validator';

export class BumpCounterDto {
  @IsIn([1, -1])
  delta!: 1 | -1;
}
