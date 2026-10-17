import { Matches } from 'class-validator';

/** Base64 (standard alphabet) of an EC P-256 public key in X.509 form (91 bytes -> 124 chars). */
export class SetE2eeKeyDto {
  @Matches(/^[A-Za-z0-9+/]{100,180}={0,2}$/)
  publicKey!: string;
}
