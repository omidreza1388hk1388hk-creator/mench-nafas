import { ConflictException, NotFoundException } from '@nestjs/common';
import { UsersService } from './users.service';
import { UsersRepository, UserRow } from './users.repository';

describe('UsersService.updateProfile', () => {
  let service: UsersService;
  let repository: jest.Mocked<UsersRepository>;

  const existingUser: UserRow = {
    id: 'user-1',
    phone_e164: '+15551234567',
    display_name: 'Old Name',
    username: null,
    avatar_url: null,
    bio: null,
    notification_privacy_mode: 'full_content',
    created_at: new Date(),
    updated_at: new Date(),
  };

  beforeEach(() => {
    repository = {
      findById: jest.fn(),
      updateProfile: jest.fn(),
    } as unknown as jest.Mocked<UsersRepository>;
    service = new UsersService(repository);
  });

  it('throws NotFoundException when the user does not exist', async () => {
    repository.findById.mockResolvedValue(null);

    await expect(service.updateProfile('missing-user', { displayName: 'New' })).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect(repository.updateProfile).not.toHaveBeenCalled();
  });

  it('passes the patch through unchanged on success', async () => {
    repository.findById.mockResolvedValue(existingUser);
    repository.updateProfile.mockResolvedValue({ ...existingUser, display_name: 'New Name' });

    const result = await service.updateProfile('user-1', { displayName: 'New Name' });

    expect(repository.updateProfile).toHaveBeenCalledWith('user-1', { displayName: 'New Name' });
    expect(result.display_name).toBe('New Name');
  });

  /**
   * Postgres reports a unique-constraint violation as error code 23505 —
   * this must surface to the Android client as a 409 ("username taken"),
   * not a generic 500, so the Account settings screen can show a specific
   * inline error instead of a dead-end failure toast.
   */
  it('translates a unique-constraint violation on username into ConflictException', async () => {
    repository.findById.mockResolvedValue(existingUser);
    repository.updateProfile.mockRejectedValue(Object.assign(new Error('duplicate key'), { code: '23505' }));

    await expect(service.updateProfile('user-1', { username: 'taken' })).rejects.toBeInstanceOf(ConflictException);
  });

  it('rethrows unrelated database errors as-is', async () => {
    repository.findById.mockResolvedValue(existingUser);
    const dbError = Object.assign(new Error('connection lost'), { code: '08006' });
    repository.updateProfile.mockRejectedValue(dbError);

    await expect(service.updateProfile('user-1', { bio: 'hi' })).rejects.toBe(dbError);
  });
});
