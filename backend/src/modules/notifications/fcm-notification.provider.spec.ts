jest.mock('firebase-admin/app', () => ({
  getApps: jest.fn(() => []),
  initializeApp: jest.fn(() => ({ name: 'fake-app' })),
  cert: jest.fn((creds) => creds),
}));

jest.mock('firebase-admin/messaging', () => ({
  getMessaging: jest.fn(() => ({ send: jest.fn() })),
}));

import { getApps, initializeApp, cert } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { FcmNotificationProvider } from './fcm-notification.provider';

describe('FcmNotificationProvider', () => {
  const credentials = {
    projectId: 'mench-test',
    clientEmail: 'test@mench-test.iam.gserviceaccount.com',
    privateKey: '-----BEGIN PRIVATE KEY-----\\nFAKEFAKEFAKE\\n-----END PRIVATE KEY-----\\n',
  };

  beforeEach(() => {
    jest.clearAllMocks();
    (getApps as jest.Mock).mockReturnValue([]);
  });

  it('initializes Firebase with the given credentials and un-escapes literal \\n in the private key', () => {
    new FcmNotificationProvider(credentials);

    expect(cert).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 'mench-test',
        clientEmail: 'test@mench-test.iam.gserviceaccount.com',
        privateKey: '-----BEGIN PRIVATE KEY-----\nFAKEFAKEFAKE\n-----END PRIVATE KEY-----\n',
      }),
    );
    expect(initializeApp).toHaveBeenCalled();
  });

  it('reuses an already-initialized default app instead of calling initializeApp again', () => {
    (getApps as jest.Mock).mockReturnValue([{ name: 'existing-app' }]);

    new FcmNotificationProvider(credentials);

    expect(initializeApp).not.toHaveBeenCalled();
  });

  it('sendToDevice calls messaging.send as a data-only message (title/body folded into data, no top-level notification field)', async () => {
    const send = jest.fn().mockResolvedValue('message-id');
    (getMessaging as jest.Mock).mockReturnValue({ send });

    const provider = new FcmNotificationProvider(credentials);
    await provider.sendToDevice('device-token', {
      title: 'Alpha',
      body: 'hello',
      data: { conversationId: 'conv-1' },
      collapseKey: 'conv-1',
    });

    expect(send).toHaveBeenCalledWith({
      token: 'device-token',
      data: { conversationId: 'conv-1', title: 'Alpha', body: 'hello' },
      android: { collapseKey: 'conv-1', priority: 'high' },
    });
    expect(send.mock.calls[0][0]).not.toHaveProperty('notification');
  });

  it('sendToDevice sends an empty string body (never omits the field) when the payload body is null', async () => {
    const send = jest.fn().mockResolvedValue('message-id');
    (getMessaging as jest.Mock).mockReturnValue({ send });

    const provider = new FcmNotificationProvider(credentials);
    await provider.sendToDevice('device-token', {
      title: 'MENCH',
      body: null,
      data: {},
      collapseKey: 'x',
    });

    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ body: '' }) }),
    );
  });

  it('sendToDevice never throws when messaging.send rejects (e.g. an unregistered/expired token)', async () => {
    const send = jest.fn().mockRejectedValue(new Error('messaging/registration-token-not-registered'));
    (getMessaging as jest.Mock).mockReturnValue({ send });

    const provider = new FcmNotificationProvider(credentials);

    await expect(
      provider.sendToDevice('dead-token', {
        title: 'Alpha',
        body: 'hello',
        data: {},
        collapseKey: 'x',
      }),
    ).resolves.toBeUndefined();
  });
});
