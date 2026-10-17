export interface PollOptionDto {
  id: string;
  text: string;
  /** Everyone who currently voted for this option (polls are not anonymous — it is a couple's / small group's chat). */
  voterIds: string[];
}

export interface PollDto {
  id: string;
  conversationId: string;
  creatorId: string;
  question: string;
  multiple: boolean;
  closed: boolean;
  createdAt: string;
  options: PollOptionDto[];
}
