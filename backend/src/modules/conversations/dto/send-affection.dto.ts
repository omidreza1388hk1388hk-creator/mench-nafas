import { IsIn } from 'class-validator';

export class SendAffectionDto {
  @IsIn(['hug', 'kiss'])
  kind!: 'hug' | 'kiss';
}
