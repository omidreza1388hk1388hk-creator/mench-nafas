import { IsIn } from 'class-validator';
import { NotificationPrivacyMode } from '../users.repository';

/** Names match master-prompt section 30's three notification privacy modes exactly. */
export class UpdateNotificationPrivacyDto {
  @IsIn(['full_content', 'sender_only', 'hide_content'])
  mode!: NotificationPrivacyMode;
}
