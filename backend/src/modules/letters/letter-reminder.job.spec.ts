import { LetterReminderJob } from './letter-reminder.job';

describe('LetterReminderJob', () => {
  let letters: Record<string, jest.Mock>;
  let conversations: Record<string, jest.Mock>;
  let users: Record<string, jest.Mock>;
  let notifications: Record<string, jest.Mock>;
  let job: LetterReminderJob;

  beforeEach(() => {
    letters = { claimDueReminders: jest.fn() };
    conversations = { listOtherMemberIds: jest.fn().mockResolvedValue(['user-b']) };
    users = { findById: jest.fn().mockResolvedValue({ display_name: 'Ali', phone_e164: '+98' }) };
    notifications = { notifyLetterUnlocked: jest.fn().mockResolvedValue(undefined) };
    job = new LetterReminderJob(letters as any, conversations as any, users as any, notifications as any);
  });

  it('pushes one reminder to the recipient of each claimed letter', async () => {
    letters.claimDueReminders.mockResolvedValue([{ id: 'l-1', conversation_id: 'c-1', author_id: 'user-a' }]);
    await expect(job.tick()).resolves.toBe(1);
    expect(conversations.listOtherMemberIds).toHaveBeenCalledWith('c-1', 'user-a');
    expect(notifications.notifyLetterUnlocked).toHaveBeenCalledWith({ recipientUserIds: ['user-b'], conversationId: 'c-1', senderDisplayName: 'Ali' });
  });

  it('does nothing when no letter is due', async () => {
    letters.claimDueReminders.mockResolvedValue([]);
    await expect(job.tick()).resolves.toBe(0);
    expect(notifications.notifyLetterUnlocked).not.toHaveBeenCalled();
  });

  it('swallows errors so a bad pass never crashes the server', async () => {
    letters.claimDueReminders.mockRejectedValue(new Error('db down'));
    await expect(job.tick()).resolves.toBe(0);
  });

  it('never runs two passes at once', async () => {
    let release: (v: any[]) => void = () => undefined;
    letters.claimDueReminders.mockReturnValue(new Promise((r) => (release = r)));
    const first = job.tick();
    await expect(job.tick()).resolves.toBe(0);
    release([]);
    await first;
    expect(letters.claimDueReminders).toHaveBeenCalledTimes(1);
  });
});
