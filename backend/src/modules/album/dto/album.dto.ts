import { IsIn, IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class CreateAlbumItemDto {
  @IsUUID()
  attachmentId!: string;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  caption?: string;
}

/** The reactions people can leave; null removes yours. */
export const ALBUM_REACTIONS = ['💗', '😍', '😂', '🥺', '🔥'] as const;

export class ReactAlbumItemDto {
  @IsOptional()
  @IsIn(ALBUM_REACTIONS as unknown as string[])
  emoji?: string | null;
}
