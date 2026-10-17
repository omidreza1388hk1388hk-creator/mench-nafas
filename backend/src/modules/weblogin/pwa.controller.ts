import { Controller, Get, Req, Res } from '@nestjs/common';
import { Response } from 'express';
import { PWA_HTML, PWA_MANIFEST, PWA_SW, PWA_ICON_SVG } from './pwa-assets';

/**
 * MENCH Web (installable PWA). Served at /api/v1/web. The page is one self-contained file: no
 * third-party scripts, so a strict CSP is possible. `<base>` is injected from the request URL so the
 * same HTML works at /web and /web/.
 */
@Controller('web')
export class PwaController {
  @Get()
  index(@Req() req: any, @Res() res: Response) {
    const base = String(req.originalUrl).split('?')[0].replace(/\/?$/, '/');
    res.set({
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-cache',
      'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'",
    });
    res.send(PWA_HTML.replace('__BASE__', base.replace(/[^A-Za-z0-9/_.-]/g, '')));
  }

  @Get('manifest.webmanifest')
  manifest(@Res() res: Response) {
    res.set({ 'Content-Type': 'application/manifest+json; charset=utf-8', 'Cache-Control': 'no-cache' });
    res.send(PWA_MANIFEST);
  }

  @Get('sw.js')
  sw(@Res() res: Response) {
    res.set({ 'Content-Type': 'application/javascript; charset=utf-8', 'Cache-Control': 'no-cache' });
    res.send(PWA_SW);
  }

  @Get('icon.svg')
  icon(@Res() res: Response) {
    res.set({ 'Content-Type': 'image/svg+xml', 'Cache-Control': 'public, max-age=86400' });
    res.send(PWA_ICON_SVG);
  }
}
