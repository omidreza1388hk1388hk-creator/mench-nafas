import { IsOptional, IsString, Length, Matches } from 'class-validator';

/**
 * All fields optional and independently patchable — the Android Account
 * settings screen sends only the fields the user actually changed, not a
 * full profile snapshot, so two devices editing different fields around
 * the same time don't clobber each other.
 */
export class UpdateProfileDto {
  @IsOptional()
  @IsString()
  @Length(1, 80)
  displayName?: string;

  // Deliberately more restrictive than displayName: usernames are a
  // unique, addressable handle (spec section 31), so they're constrained
  // to a safe, unambiguous character set up front rather than validated
  // only against the DB's UNIQUE constraint.
  @IsOptional()
  @IsString()
  @Length(3, 32)
  @Matches(/^[a-zA-Z0-9_]+$/, {
    message: 'username may only contain letters, numbers, and underscores',
  })
  username?: string;

  @IsOptional()
  @IsString()
  @Length(0, 200)
  bio?: string;
}
