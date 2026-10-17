import { IsIn } from 'class-validator';

/** Keep in sync with MessageEffect.wire on Android (feature/chat/presentation/MessageEffects.kt). */
export const SCREEN_EFFECTS = ['hearts', 'confetti', 'night', 'lanterns', 'balloons'] as const;

export class SendScreenEffectDto {
  @IsIn(SCREEN_EFFECTS as unknown as string[])
  effect!: string;
}
