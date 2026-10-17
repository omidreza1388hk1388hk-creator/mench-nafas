import { NotFoundException } from '@nestjs/common';
import { DevicesController } from './devices.controller';
import { DevicesRepository } from './devices.repository';
import { SessionsRepository } from '../sessions/sessions.repository';

describe('DevicesController (settings: current device + log out others)', () => {
  let devices: jest.Mocked<DevicesRepository>;
  let sessions: jest.Mocked<SessionsRepository>;
  let controller: DevicesController;
  const req = { user: { sub: 'user-1', deviceId: 'dev-a' } };
  const row = (id: string) => ({
    id,
    device_name: `Phone ${id}`,
    platform: 'android',
    last_seen_at: new Date('2026-10-01T10:00:00Z'),
    created_at: new Date('2026-09-01T10:00:00Z'),
  });

  beforeEach(() => {
    devices = {
      listActiveForUser: jest.fn(),
      revokeAllExcept: jest.fn(),
      findByIdForUser: jest.fn(),
      revoke: jest.fn(),
    } as unknown as jest.Mocked<DevicesRepository>;
    sessions = {
      revokeAllExceptDevice: jest.fn(),
      revokeAllForDevice: jest.fn(),
    } as unknown as jest.Mocked<SessionsRepository>;
    controller = new DevicesController(devices, sessions);
  });

  it('marks only the requesting device as current and never leaks push tokens', async () => {
    devices.listActiveForUser.mockResolvedValue([row('dev-a'), row('dev-b')] as never);
    const result = await controller.listDevices(req);
    expect(result.map((d) => [d.id, d.isCurrent])).toEqual([['dev-a', true], ['dev-b', false]]);
    expect(Object.keys(result[0])).not.toContain('push_token');
  });

  it('logs out every other device and session, keeping the caller', async () => {
    devices.revokeAllExcept.mockResolvedValue(2);
    const result = await controller.revokeOtherDevices(req);
    expect(devices.revokeAllExcept).toHaveBeenCalledWith('user-1', 'dev-a');
    expect(sessions.revokeAllExceptDevice).toHaveBeenCalledWith('user-1', 'dev-a');
    expect(result).toEqual({ ok: true, revoked: 2 });
  });

  it('still returns 404 for a device that is not the caller\'s', async () => {
    devices.findByIdForUser.mockResolvedValue(null);
    await expect(
      controller.revokeDevice(req, '11111111-1111-4111-8111-111111111111'),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(devices.revoke).not.toHaveBeenCalled();
  });
});
