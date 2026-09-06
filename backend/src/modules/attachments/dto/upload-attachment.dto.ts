import { Type } from 'class-transformer';
import { IsInt, IsOptional, Min } from 'class-validator';

/**
 * Bound from multipart form fields alongside the file itself (NestJS's
 * FileInterceptor/FileFieldsInterceptor parse non-file multipart fields
 * into the same request body @Body() reads from). Required only for
 * audio/video uploads — enforced in AttachmentsService once the mime type
 * is known, not here, since this DTO has no way to see the uploaded
 * file's mime type. widthPx/heightPx are video-only (client-reported —
 * see AttachmentsService's doc comment on why).
 */
export class UploadAttachmentDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  durationMs?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  widthPx?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  heightPx?: number;
}
