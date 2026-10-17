import { IsArray, ArrayMaxSize, ArrayMinSize } from 'class-validator';

/** Shape and ranges are validated by sanitizeStrokes in the service. */
export class AppendStrokesDto {
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(40)
  strokes!: unknown[];
}
