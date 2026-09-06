import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { IceServerDto } from './call.dto';

@Injectable()
export class IceServersService implements OnModuleInit {
  private readonly logger = new Logger('IceServersService');

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    if (!this.config.get<string>('TURN_URL')) {
      this.logger.warn(
        'TURN_URL is not configured — calls will only work between peers that ' +
          'can reach each other via STUN (direct or simple-NAT connections). ' +
          'See .env.example.',
      );
    }
  }

  build(): IceServerDto[] {
    const servers: IceServerDto[] = [];

    const stunUrls = (this.config.get<string>('STUN_URLS') ?? '')
      .split(',')
      .map((u) => u.trim())
      .filter(Boolean);
    if (stunUrls.length > 0) {
      servers.push({ urls: stunUrls, username: null, credential: null });
    }

    const turnUrl = this.config.get<string>('TURN_URL');
    if (turnUrl) {
      const turnUrls = turnUrl
        .split(',')
        .map((u) => u.trim())
        .filter(Boolean);
      servers.push({
        urls: turnUrls,
        username: this.config.get<string>('TURN_USERNAME') || null,
        credential: this.config.get<string>('TURN_CREDENTIAL') || null,
      });
    }

    return servers;
  }
}
