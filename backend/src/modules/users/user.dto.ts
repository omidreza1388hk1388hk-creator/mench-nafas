import { UserRow } from './users.repository';

export interface UserDto {
  id: string;
  phoneE164: string;
  displayName: string | null;
  username: string | null;
  avatarUrl: string | null;
  bio: string | null;
  /** Profile-photo version (ms timestamp) for ?v= cache busting; null = no photo. */
  avatarVersion: number | null;
}

export function toUserDto(row: UserRow): UserDto {
  return {
    id: row.id,
    phoneE164: row.phone_e164,
    displayName: row.display_name,
    username: row.username,
    avatarUrl: row.avatar_url,
    bio: row.bio,
    avatarVersion: row.avatar_version ? Number(row.avatar_version) : null,
  };
}
