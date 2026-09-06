import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsUUID, Max, Min } from 'class-validator';

export type SearchScope = 'all' | 'messages' | 'conversations' | 'files';

export class SearchQueryDto {
  // Optional, not required: an empty query with scope=files is a valid
  // "browse all shared files" request (see SearchRepository.searchFiles).
  // messages/conversations searches with an empty query simply return
  // near-everything ILIKE'd against '%%' — the Android client's
  // SearchViewModel debounces and only calls the endpoint once the user
  // has actually typed something, but the server doesn't rely on that.
  @IsOptional()
  q?: string;

  @IsOptional()
  @IsIn(['all', 'messages', 'conversations', 'files'])
  scope?: SearchScope;

  @IsOptional()
  @IsUUID()
  conversationId?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit?: number;
}
