import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { TokenService } from './token.service';

/**
 * Verifies the Authorization: Bearer <accessToken> header on protected
 * routes and attaches the decoded claims to req.user. Server-side
 * authorization always happens here — the Android client's UI state is
 * never trusted as an authorization boundary (spec section 4/34).
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly tokenService: TokenService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest();
    const authHeader: string | undefined = request.headers.authorization;

    if (!authHeader?.startsWith('Bearer ')) {
      throw new UnauthorizedException('Missing bearer token');
    }

    const token = authHeader.slice('Bearer '.length);

    try {
      request.user = this.tokenService.verifyAccessToken(token);
      return true;
    } catch {
      throw new UnauthorizedException('Invalid or expired access token');
    }
  }
}
