import { IsBoolean, IsIn, IsInt, IsOptional, IsUUID, Max, Min } from 'class-validator';

/** Phase 3 — Together mode: live-synced playback of a chat's audio/video. Relay only, nothing is stored. */
export const TOGETHER_KINDS = ['start', 'sync', 'stop'] as const;

export class SendTogetherDto {
  @IsIn(TOGETHER_KINDS as unknown as string[])
  kind!: (typeof TOGETHER_KINDS)[number];

  @IsOptional()
  @IsUUID()
  attachmentId?: string;

  @IsInt()
  @Min(0)
  @Max(86_400_000)
  positionMs!: number;

  @IsBoolean()
  playing!: boolean;
}
