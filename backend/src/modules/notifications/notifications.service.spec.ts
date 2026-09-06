import { NotificationsService } from './notifications.service';
import { DevicesRepository, DeviceRow } from '../devices/devices.repository';
import { UsersService } from '../users/users.service';
import { RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { NotificationProvider } from './notification-provider.interface';
import { UserRow } from '../users/users.repository';

function makeDevice(overrides: Partial<DeviceRow> = {}): DeviceRow {
  return {
    id: 'device-1',
    user_id: 'user-1',
    device_name: 'Pixel',
    platform: 'android',
    last_seen_at: null,
    created_at: new Date('2026-01-01T00:00:00.000Z'),
    revoked_at: null,
    push_token: 'fake-token',
    push_provider: 'fcm',
    push_token_updated_at: new Date('2026-01-01T00:00:00.000Z'),
    ...overrides,
  };
}

function makeUser(overrides: Partial<UserRow> = {}): UserRow {
  return {
    id: 'user-1',
    phone_e164: '+15551234567',
    display_name: 'Alpha',
    username: null,
    avatar_url: null,
    bio: null,
    created_at: new Date('2026-01-01T00:00:00.000Z'),
    updated_at: new Date('2026-01-01T00:00:00.000Z'),
    notification_privacy_mode: 'full_content',
    ...overrides,
  };
}

describe('NotificationsService', () => {
  let service: NotificationsService;
  let devices: jest.Mocked<DevicesRepository>;
  let users: jest.Mocked<UsersService>;
  let broadcaster: jest.Mocked<RealtimeBroadcaster>;
  let provider: jest.Mocked<NotificationProvider>;

  beforeEach(() => {
    devices = {
      listPushableForUser: jest.fn(),
    } as unknown as jest.Mocked<DevicesRepository>;

    users = {
      findById: jest.fn(),
    } as unknown as jest.Mocked<UsersService>;

    broadcaster = {
      broadcastToUsers: jest.fn(),
      isUserConnected: jest.fn(),
    } as unknown as jest.Mocked<RealtimeBroadcaster>;

    provider = {
      sendToDevice: jest.fn(),
    } as unknown as jest.Mocked<NotificationProvider>;

    service = new NotificationsService(devices, users, broadcaster, provider);
  });

  describe('notifyNewMessage', () => {
    it('skips a recipient who has an open WebSocket connection', async () => {
      broadcaster.isUserConnected.mockReturnValue(true);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: null,
        senderDisplayName: 'Alpha',
        messageKind: 'text',
        messageBody: 'hi',
      });

      expect(users.findById).not.toHaveBeenCalled();
      expect(provider.sendToDevice).not.toHaveBeenCalled();
    });

    it('skips a recipient with no registered devices without throwing', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser());
      devices.listPushableForUser.mockResolvedValue([]);

      await expect(
        service.notifyNewMessage({
          recipientUserIds: ['user-1'],
          conversationId: 'conv-1',
          conversationTitle: null,
          senderDisplayName: 'Alpha',
          messageKind: 'text',
          messageBody: 'hi',
        }),
      ).resolves.toBeUndefined();

      expect(provider.sendToDevice).not.toHaveBeenCalled();
    });

    it('full_content: shows sender name and a truncated body for a direct conversation', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser({ notification_privacy_mode: 'full_content' }));
      devices.listPushableForUser.mockResolvedValue([makeDevice()]);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: null,
        senderDisplayName: 'Alpha',
        messageKind: 'text',
        messageBody: 'hello there',
      });

      expect(provider.sendToDevice).toHaveBeenCalledWith('fake-token', {
        title: 'Alpha',
        body: 'hello there',
        data: { conversationId: 'conv-1', type: 'message' },
        collapseKey: 'conv-1',
      });
    });

    it('full_content: prefixes the sender name in the body for a group conversation', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser({ notification_privacy_mode: 'full_content' }));
      devices.listPushableForUser.mockResolvedValue([makeDevice()]);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: 'Trip planning',
        senderDisplayName: 'Alpha',
        messageKind: 'text',
        messageBody: 'hello there',
      });

      expect(provider.sendToDevice).toHaveBeenCalledWith(
        'fake-token',
        expect.objectContaining({ title: 'Trip planning', body: 'Alpha: hello there' }),
      );
    });

    it('full_content: never leaks raw content for non-text kinds', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser({ notification_privacy_mode: 'full_content' }));
      devices.listPushableForUser.mockResolvedValue([makeDevice()]);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: null,
        senderDisplayName: 'Alpha',
        messageKind: 'image',
        messageBody: null,
      });

      expect(provider.sendToDevice).toHaveBeenCalledWith(
        'fake-token',
        expect.objectContaining({ body: '[photo]' }),
      );
    });

    it('sender_only: shows who, hides what', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser({ notification_privacy_mode: 'sender_only' }));
      devices.listPushableForUser.mockResolvedValue([makeDevice()]);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: null,
        senderDisplayName: 'Alpha',
        messageKind: 'text',
        messageBody: 'a secret',
      });

      expect(provider.sendToDevice).toHaveBeenCalledWith(
        'fake-token',
        expect.objectContaining({ title: 'Alpha', body: 'New message' }),
      );
    });

    it('hide_content: reveals neither sender nor content', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser({ notification_privacy_mode: 'hide_content' }));
      devices.listPushableForUser.mockResolvedValue([makeDevice()]);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: 'Trip planning',
        senderDisplayName: 'Alpha',
        messageKind: 'text',
        messageBody: 'a secret',
      });

      expect(provider.sendToDevice).toHaveBeenCalledWith(
        'fake-token',
        expect.objectContaining({ title: 'MENCH', body: 'You have a new message' }),
      );
    });

    it('reads privacy mode from the RECIPIENT, not any sender-side setting', async () => {
      // Regression guard for the exact bug class called out in the
      // service's doc comment: this test has only one user fixture
      // (the recipient) in play at all, so there is no sender row for
      // the implementation to accidentally read from — if it ever did
      // read a "sender" privacy mode, there'd be no such field on
      // NotifyNewMessageParams to read in the first place, and this
      // test would fail loudly on an undefined-mode branch instead of
      // silently passing.
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser({ id: 'user-1', notification_privacy_mode: 'hide_content' }));
      devices.listPushableForUser.mockResolvedValue([makeDevice()]);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: null,
        senderDisplayName: 'Whoever is sending',
        messageKind: 'text',
        messageBody: 'body',
      });

      expect(users.findById).toHaveBeenCalledWith('user-1');
      expect(provider.sendToDevice).toHaveBeenCalledWith('fake-token', expect.objectContaining({ title: 'MENCH' }));
    });

    it('sends to every registered device for a recipient with multiple devices', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValue(makeUser());
      devices.listPushableForUser.mockResolvedValue([
        makeDevice({ id: 'device-1', push_token: 'token-1' }),
        makeDevice({ id: 'device-2', push_token: 'token-2' }),
      ]);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1'],
        conversationId: 'conv-1',
        conversationTitle: null,
        senderDisplayName: 'Alpha',
        messageKind: 'text',
        messageBody: 'hi',
      });

      expect(provider.sendToDevice).toHaveBeenCalledTimes(2);
      expect(provider.sendToDevice).toHaveBeenCalledWith('token-1', expect.anything());
      expect(provider.sendToDevice).toHaveBeenCalledWith('token-2', expect.anything());
    });

    it('one recipient failing (e.g. provider throws) does not stop other recipients from being notified', async () => {
      broadcaster.isUserConnected.mockReturnValue(false);
      users.findById.mockResolvedValueOnce(makeUser({ id: 'user-1' })).mockResolvedValueOnce(makeUser({ id: 'user-2' }));
      devices.listPushableForUser
        .mockResolvedValueOnce([makeDevice({ push_token: 'token-1' })])
        .mockResolvedValueOnce([makeDevice({ push_token: 'token-2' })]);
      provider.sendToDevice.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(undefined);

      await service.notifyNewMessage({
        recipientUserIds: ['user-1', 'user-2'],
        conversationId: 'conv-1',
        conversationTitle: null,
        senderDisplayName: 'Alpha',
        messageKind: 'text',
        messageBody: 'hi',
      });

      expect(provider.sendToDevice).toHaveBeenCalledTimes(2);
    });
  });

  describe('notifyAddedToGroup', () => {
    it('notifies the new member even when they have an open connection (unlike notifyNewMessage)', async () => {
      users.findById.mockResolvedValue(makeUser());
      devices.listPushableForUser.mockResolvedValue([makeDevice()]);
      broadcaster.isUserConnected.mockReturnValue(true); // deliberately "connected"

      await service.notifyAddedToGroup({
        recipientUserId: 'user-1',
        conversationId: 'conv-1',
        conversationTitle: 'Trip planning',
        addedByDisplayName: 'Alpha',
      });

      expect(broadcaster.isUserConnected).not.toHaveBeenCalled();
      expect(provider.sendToDevice).toHaveBeenCalledWith(
        'fake-token',
        expect.objectContaining({ title: 'Trip planning', body: 'Alpha added you to the group' }),
      );
    });

    it('does nothing (and does not throw) if the recipient no longer exists', async () => {
      users.findById.mockResolvedValue(null);

      await expect(
        service.notifyAddedToGroup({
          recipientUserId: 'user-1',
          conversationId: 'conv-1',
          conversationTitle: 'Trip planning',
          addedByDisplayName: 'Alpha',
        }),
      ).resolves.toBeUndefined();

      expect(provider.sendToDevice).not.toHaveBeenCalled();
    });
  });
});
