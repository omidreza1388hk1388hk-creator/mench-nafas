export interface ScheduledMessageDto {
  id: string;
  conversationId: string;
  body: string;
  silent: boolean;
  sendAt: string;
  status: 'pending' | 'sending' | 'sent' | 'failed';
  createdAt: string;
}
