import { DevNotificationProvider } from './dev-notification.provider';

describe('DevNotificationProvider', () => {
  it('never throws, regardless of payload shape', async () => {
    const provider = new DevNotificationProvider();

    await expect(
      provider.sendToDevice('any-token', {
        title: 'Title',
        body: 'Body',
        data: { conversationId: 'conv-1' },
        collapseKey: 'conv-1',
      }),
    ).resolves.toBeUndefined();
  });

  it('handles a null body without throwing', async () => {
    const provider = new DevNotificationProvider();

    await expect(
      provider.sendToDevice('any-token', {
        title: 'Title',
        body: null,
        data: {},
        collapseKey: 'x',
      }),
    ).resolves.toBeUndefined();
  });
});
