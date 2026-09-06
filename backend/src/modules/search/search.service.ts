import { Injectable } from '@nestjs/common';
import { SearchRepository } from './search.repository';
import { SearchScope } from './dto/search-query.dto';
import {
  SearchResultsDto,
  toConversationSearchResultDto,
  toFileSearchResultDto,
  toMessageSearchResultDto,
} from './search.dto';

const DEFAULT_LIMIT = 20;

@Injectable()
export class SearchService {
  constructor(private readonly searchRepository: SearchRepository) {}

  /**
   * Runs only the sub-searches the requested scope actually needs — a
   * 'messages'-scoped search never touches attachments, e.g. — but always
   * returns all three arrays (empty ones for skipped scopes) so the
   * response shape never depends on the request, matching how every other
   * endpoint in this API returns a fixed DTO shape.
   */
  async search(
    userId: string,
    query: string | undefined,
    scope: SearchScope | undefined,
    conversationId: string | undefined,
    limit: number | undefined,
  ): Promise<SearchResultsDto> {
    const effectiveScope = scope ?? 'all';
    const effectiveLimit = limit ?? DEFAULT_LIMIT;
    const trimmedQuery = (query ?? '').trim();
    const effectiveConversationId = conversationId ?? null;

    const wantsMessages = effectiveScope === 'all' || effectiveScope === 'messages';
    const wantsConversations = effectiveScope === 'all' || effectiveScope === 'conversations';
    const wantsFiles = effectiveScope === 'all' || effectiveScope === 'files';

    // A blank query only makes sense for "browse files" (see
    // SearchRepository.searchFiles) — for messages/conversations it would
    // ILIKE '%%' every row the user can see, which is expensive and not a
    // real product need, so those two are simply skipped when empty.
    const [messageRows, conversationRows, fileRows] = await Promise.all([
      wantsMessages && trimmedQuery.length > 0
        ? this.searchRepository.searchMessages(userId, trimmedQuery, effectiveLimit, effectiveConversationId)
        : Promise.resolve([]),
      wantsConversations && trimmedQuery.length > 0
        ? this.searchRepository.searchConversations(userId, trimmedQuery, effectiveLimit)
        : Promise.resolve([]),
      wantsFiles
        ? this.searchRepository.searchFiles(userId, trimmedQuery, effectiveLimit, effectiveConversationId)
        : Promise.resolve([]),
    ]);

    return {
      messages: messageRows.map(toMessageSearchResultDto),
      conversations: conversationRows.map(toConversationSearchResultDto),
      files: fileRows.map(toFileSearchResultDto),
    };
  }
}
