import { BadRequestException, ConflictException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { ConversationsService } from '../conversations/conversations.service';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { PollDto } from './poll.dto';
import { PollsRepository } from './polls.repository';

export interface CreatePollInput {
  conversationId: string;
  creatorId: string;
  question: string;
  options: string[];
  multiple?: boolean;
}

/** Trims, drops blanks and case-insensitive duplicates; keeps the first spelling. */
export function cleanOptions(options: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of options) {
    const text = raw.replace(/\s+/g, ' ').trim();
    if (!text) continue;
    const key = text.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(text);
  }
  return out;
}

@Injectable()
export class PollsService {
  constructor(
    private readonly pollsRepository: PollsRepository,
    private readonly conversationsService: ConversationsService,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly broadcaster: RealtimeBroadcaster,
  ) {}

  /** Creates the poll only. The client then sends the chat message "poll:v1:<id>" through the normal send path. */
  async create(input: CreatePollInput): Promise<PollDto> {
    await this.conversationsService.assertMember(input.conversationId, input.creatorId);
    const question = input.question.replace(/\s+/g, ' ').trim();
    if (!question) throw new BadRequestException('Question is required');
    const options = cleanOptions(input.options);
    if (options.length < 2) throw new BadRequestException('A poll needs at least two different options');
    const id = await this.pollsRepository.create({
      conversationId: input.conversationId,
      creatorId: input.creatorId,
      question,
      multiple: input.multiple === true,
      options,
    });
    return this.require(id);
  }

  async get(pollId: string, userId: string): Promise<PollDto> {
    const poll = await this.require(pollId);
    await this.conversationsService.assertMember(poll.conversationId, userId);
    return poll;
  }

  async vote(pollId: string, userId: string, optionIds: string[]): Promise<PollDto> {
    const poll = await this.get(pollId, userId);
    if (poll.closed) throw new ConflictException('This poll is closed');
    const unique = Array.from(new Set(optionIds));
    if (!poll.multiple && unique.length > 1) throw new BadRequestException('This poll allows one answer');
    const valid = new Set(await this.pollsRepository.listOptionIds(pollId));
    if (unique.some((id) => !valid.has(id))) throw new BadRequestException('Unknown option');
    await this.pollsRepository.replaceVotes(pollId, userId, unique);
    return this.publish(pollId, userId);
  }

  async close(pollId: string, userId: string): Promise<PollDto> {
    const poll = await this.get(pollId, userId);
    if (poll.creatorId !== userId) throw new ForbiddenException('Only the poll creator can close it');
    await this.pollsRepository.close(pollId);
    return this.publish(pollId, userId);
  }

  private async publish(pollId: string, actingUserId: string): Promise<PollDto> {
    const poll = await this.require(pollId);
    const others = await this.conversationsRepository.listOtherMemberIds(poll.conversationId, actingUserId);
    this.broadcaster.broadcastToUsers(others, { type: 'poll.updated', conversationId: poll.conversationId, poll });
    return poll;
  }

  private async require(pollId: string): Promise<PollDto> {
    const poll = await this.pollsRepository.findDto(pollId);
    if (!poll) throw new NotFoundException('Poll not found');
    return poll;
  }
}
