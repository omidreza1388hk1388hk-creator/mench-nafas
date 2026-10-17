import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { LinkPreviewService } from './link-preview.service';

@UseGuards(JwtAuthGuard)
@Controller('link-preview')
export class LinkPreviewController {
  constructor(private readonly linkPreviewService: LinkPreviewService) {}

  /** GET /link-preview?url=https://… → { url, title, description, siteName } or null when there is nothing to show. */
  @Get()
  @Throttle({ default: { limit: 40, ttl: 60_000 } })
  async get(@Query('url') url: string) {
    return (await this.linkPreviewService.preview(url ?? '')) ?? { url, title: null, description: null, siteName: null };
  }
}
