import { IsInt, Max, Min } from 'class-validator';

/** How long the sender held the heart. Bounded so a stuck client cannot keep a partner's phone vibrating. */
export class SendHeartbeatDto {
  @IsInt()
  @Min(1000)
  @Max(10000)
  durationMs!: number;
}
