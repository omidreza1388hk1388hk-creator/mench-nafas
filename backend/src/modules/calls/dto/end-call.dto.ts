import { IsIn, IsOptional } from 'class-validator';

/**
 * reason is client-supplied diagnostic context only (e.g. "network_error",
 * "ice_failed") — it is never used for authorization or to decide the
 * resulting status. CallsService.end derives the actual status
 * ('ended' vs 'missed') itself from whether the call had been answered,
 * so a client can't misreport what happened by sending a misleading
 * reason (spec section 4: never trust the client for anything that
 * matters).
 */
export class EndCallDto {
  @IsOptional()
  @IsIn(['user_hangup', 'network_error', 'ice_failed', 'timeout', 'other'])
  reason?: string;
}
