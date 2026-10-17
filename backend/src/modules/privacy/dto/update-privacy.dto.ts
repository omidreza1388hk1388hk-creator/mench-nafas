import { IsBoolean, IsIn, IsOptional } from 'class-validator';

const VISIBILITY = ['everybody', 'nobody'];

/** Every field optional: send only what changed. */
export class UpdatePrivacyDto {
  @IsOptional() @IsIn(VISIBILITY) lastSeen?: 'everybody' | 'nobody';
  @IsOptional() @IsIn(VISIBILITY) profilePhoto?: 'everybody' | 'nobody';
  @IsOptional() @IsIn(VISIBILITY) addMe?: 'everybody' | 'nobody';
  @IsOptional() @IsIn(VISIBILITY) calls?: 'everybody' | 'nobody';
  @IsOptional() @IsBoolean() readReceipts?: boolean;
}
