import { Inject, Injectable, Logger } from '@nestjs/common';
import { DevicesRepository } from '../devices/devices.repository';
import { UsersService } from '../users/users.service';
import { NotificationPrivacyMode } from '../users/users.repository';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { NOTIFICATION_PROVIDER, NotificationPayload, NotificationProvider } from './notification-provider.interface';
import { describeRichBody } from '../messages/rich-body';

export interface NotifyNewMessageParams {
  recipientUserIds: string[];
  conversationId: string;
  /** null for a direct (1:1) conversation — those have no title of their own; the recipient's own client already knows who's messaging them. */
  conversationTitle: string | null;
  senderDisplayName: string;
  messageKind: string; // 'text' | 'image' | 'file' | 'audio' | 'video'
  messageBody: string | null;
}

export interface NotifyAddedToGroupParams {
  recipientUserId: string;
  conversationId: string;
  conversationTitle: string;
  addedByDisplayName: string;
}

export interface NotifyMissYouParams {
  recipientUserIds: string[];
  conversationId: string;
  senderDisplayName: string;
}

export interface NotifyLetterReceivedParams {
  recipientUserIds: string[];
  conversationId: string;
  senderDisplayName: string;
}

export interface NotifyAffectionParams {
  recipientUserIds: string[];
  conversationId: string;
  senderDisplayName: string;
  kind: 'hug' | 'kiss';
}

const MAX_BODY_PREVIEW_LENGTH = 120;

/** Human-readable placeholder text for non-text message kinds — never leak a raw storage URL or binary description into a notification. */
function describeMessageKind(kind: string): string {
  switch (kind) {
    case 'image':
      return '[photo]';
    case 'video':
      return '[video]';
    case 'audio':
      return '[voice message]';
    case 'file':
      return '[file]';
    default:
      return '[message]';
  }
}

function truncate(text: string): string {
  if (text.length <= MAX_BODY_PREVIEW_LENGTH) return text;
  return `${text.slice(0, MAX_BODY_PREVIEW_LENGTH - 1)}…`;
}

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger('NotificationsService');

  constructor(
    private readonly devicesRepository: DevicesRepository,
    private readonly usersService: UsersService,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
    @Inject(NOTIFICATION_PROVIDER) private readonly provider: NotificationProvider,
  ) {}

  /**
   * Called from MessagesService.send() after the WebSocket broadcast.
   * Fire-and-forget from the caller's point of view: a notification
   * failure must never fail the message-send request itself, so this
   * method swallows and logs its own errors per-recipient rather than
   * letting one bad recipient/device abort the loop for the rest.
   */
  async notifyNewMessage(params: NotifyNewMessageParams): Promise<void> {
    for (const recipientUserId of params.recipientUserIds) {
      try {
        // Skip a recipient with at least one open WebSocket connection —
        // they already got message.created live. This is a per-user, not
        // per-device, check (see RealtimeBroadcaster.isUserConnected's
        // doc comment for the accepted trade-off).
        if (this.realtimeBroadcaster.isUserConnected(recipientUserId)) {
          continue;
        }

        const recipient = await this.usersService.findById(recipientUserId);
        if (!recipient) continue; // deleted/nonexistent user — nothing to notify

        const payload = this.buildMessagePayload(recipient.notification_privacy_mode, params);
        await this.sendToAllDevices(recipientUserId, payload);
      } catch (err) {
        this.logger.warn(
          `notifyNewMessage failed for recipient (continuing with remaining recipients): ${(err as Error).message}`,
        );
      }
    }
  }

  /**
   * Called from ConversationsService.addMembers(). Always notifies the
   * newly-added member regardless of connection state — group.member_added
   * over the socket tells clients who ALREADY have the conversation
   * cached to update it; a brand-new member has no way to discover the
   * conversation from a WebSocket event alone if their client isn't
   * already subscribed to it, so this is not a duplicate the way a
   * connected-recipient message push would be.
   */
  async notifyAddedToGroup(params: NotifyAddedToGroupParams): Promise<void> {
    try {
      const recipient = await this.usersService.findById(params.recipientUserId);
      if (!recipient) return;

      const payload = this.buildGroupAddPayload(recipient.notification_privacy_mode, params);
      await this.sendToAllDevices(params.recipientUserId, payload);
    } catch (err) {
      this.logger.warn(`notifyAddedToGroup failed (non-fatal): ${(err as Error).message}`);
    }
  }

  /**
   * Phase 9 — "I miss you". Skips connected recipients (the live miss_you
   * event already played the animation). Hidden-content mode shows the
   * generic text, like every other notification.
   */
  async notifyMissYou(params: NotifyMissYouParams): Promise<void> {
    for (const recipientUserId of params.recipientUserIds) {
      try {
        if (this.realtimeBroadcaster.isUserConnected(recipientUserId)) continue;
        const recipient = await this.usersService.findById(recipientUserId);
        if (!recipient) continue;

        const hidden = recipient.notification_privacy_mode === 'hide_content';
        await this.sendToAllDevices(recipientUserId, {
          title: hidden ? 'MENCH' : params.senderDisplayName,
          body: hidden ? 'You have a new message' : 'دلم برات تنگ شده 💗',
          data: { conversationId: params.conversationId, type: 'miss_you' },
          collapseKey: `miss-you-${params.conversationId}`,
        });
      } catch (err) {
        this.logger.warn(`notifyMissYou failed (non-fatal): ${(err as Error).message}`);
      }
    }
  }

  /** Phase 9 — a sealed letter arrived. Never includes the title/body (they're meant to be a surprise). Skips connected recipients (the live letter.received event covers them). */
  async notifyLetterReceived(params: NotifyLetterReceivedParams): Promise<void> {
    for (const recipientUserId of params.recipientUserIds) {
      try {
        if (this.realtimeBroadcaster.isUserConnected(recipientUserId)) continue;
        const recipient = await this.usersService.findById(recipientUserId);
        if (!recipient) continue;
        const hidden = recipient.notification_privacy_mode === 'hide_content';
        await this.sendToAllDevices(recipientUserId, {
          title: hidden ? 'MENCH' : params.senderDisplayName,
          body: hidden ? 'You have a new message' : 'برات یه نامه نوشته 💌',
          data: { conversationId: params.conversationId, type: 'letter' },
          collapseKey: `letter-${params.conversationId}`,
        });
      } catch (err) {
        this.logger.warn(`notifyLetterReceived failed (non-fatal): ${(err as Error).message}`);
      }
    }
  }

  /** Phase 3A — a time-capsule letter just became openable. Like notifyLetterReceived, it never includes the title or body. */
  async notifyLetterUnlocked(params: NotifyLetterReceivedParams): Promise<void> {
    for (const recipientUserId of params.recipientUserIds) {
      try {
        const recipient = await this.usersService.findById(recipientUserId);
        if (!recipient) continue;
        const hidden = recipient.notification_privacy_mode === 'hide_content';
        await this.sendToAllDevices(recipientUserId, {
          title: hidden ? 'MENCH' : params.senderDisplayName,
          body: hidden ? 'You have a new message' : 'نامه‌ای که برات نوشته بود حالا آماده‌ی باز شدنه 💌',
          data: { conversationId: params.conversationId, type: 'letter' },
          collapseKey: `letter-unlocked-${params.conversationId}`,
        });
      } catch (err) {
        this.logger.warn(`notifyLetterUnlocked failed (non-fatal): ${(err as Error).message}`);
      }
    }
  }

  /** Phase 9 — hug/kiss push, only when the recipient has no live socket (the live event already animates). */
  async notifyAffection(params: NotifyAffectionParams): Promise<void> {
    for (const recipientUserId of params.recipientUserIds) {
      try {
        if (this.realtimeBroadcaster.isUserConnected(recipientUserId)) continue;
        const recipient = await this.usersService.findById(recipientUserId);
        if (!recipient) continue;
        const hidden = recipient.notification_privacy_mode === 'hide_content';
        await this.sendToAllDevices(recipientUserId, {
          title: hidden ? 'MENCH' : params.senderDisplayName,
          body: hidden ? 'You have a new message' : params.kind === 'hug' ? 'بغلت کرد 🤗' : 'برات بوس فرستاد 😘',
          data: { conversationId: params.conversationId, type: 'affection' },
          collapseKey: `affection-${params.conversationId}`,
        });
      } catch (err) {
        this.logger.warn(`notifyAffection failed (non-fatal): ${(err as Error).message}`);
      }
    }
  }

  private async sendToAllDevices(userId: string, payload: NotificationPayload): Promise<void> {
    const devices = await this.devicesRepository.listPushableForUser(userId);
    for (const device of devices) {
      if (!device.push_token) continue; // listPushableForUser already filters this, but stay defensive
      await this.provider.sendToDevice(device.push_token, payload);
    }
  }

  /**
   * Privacy mode is always read from the RECIPIENT's own setting, never
   * the sender's — mixing these up is the obvious bug to introduce here
   * and would mean a sender's preference controls what a recipient's
   * lock screen reveals, which is backwards.
   */
  private buildMessagePayload(mode: NotificationPrivacyMode, params: NotifyNewMessageParams): NotificationPayload {
    const richPreview = params.messageKind === 'text' && params.messageBody ? describeRichBody(params.messageBody) : null;
    const bodyPreview = richPreview
      ?? (params.messageKind === 'text' && params.messageBody
        ? truncate(params.messageBody)
        : describeMessageKind(params.messageKind));

    const conversationLabel = params.conversationTitle ?? params.senderDisplayName;

    switch (mode) {
      case 'full_content':
        return {
          title: conversationLabel,
          body: params.conversationTitle ? `${params.senderDisplayName}: ${bodyPreview}` : bodyPreview,
          data: { conversationId: params.conversationId, type: 'message' },
          collapseKey: params.conversationId,
        };
      case 'sender_only':
        return {
          title: conversationLabel,
          body: 'New message',
          data: { conversationId: params.conversationId, type: 'message' },
          collapseKey: params.conversationId,
        };
      case 'hide_content':
        return {
          title: 'MENCH',
          body: 'You have a new message',
          data: { conversationId: params.conversationId, type: 'message' },
          collapseKey: params.conversationId,
        };
    }
  }

  private buildGroupAddPayload(mode: NotificationPrivacyMode, params: NotifyAddedToGroupParams): NotificationPayload {
    switch (mode) {
      case 'full_content':
        return {
          title: params.conversationTitle,
          body: `${params.addedByDisplayName} added you to the group`,
          data: { conversationId: params.conversationId, type: 'group_added' },
          collapseKey: `group-added-${params.conversationId}`,
        };
      case 'sender_only':
        return {
          title: params.conversationTitle,
          body: 'You were added to a group',
          data: { conversationId: params.conversationId, type: 'group_added' },
          collapseKey: `group-added-${params.conversationId}`,
        };
      case 'hide_content':
        return {
          title: 'MENCH',
          body: 'You were added to a new conversation',
          data: { conversationId: params.conversationId, type: 'group_added' },
          collapseKey: `group-added-${params.conversationId}`,
        };
    }
  }
}
