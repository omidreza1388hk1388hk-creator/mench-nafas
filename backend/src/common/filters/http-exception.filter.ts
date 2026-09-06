import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Response } from 'express';

/**
 * Normalizes every error into a consistent { error: { code, message } }
 * shape and makes sure raw internal errors (stack traces, DB error text)
 * never leak to the client — only ever logged server-side.
 */
@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger('ExceptionFilter');

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      const body = exception.getResponse();
      response.status(status).json({
        error: {
          code: status,
          message: typeof body === 'string' ? body : (body as any).message ?? 'Error',
        },
      });
      return;
    }

    // Unknown/unexpected error: log full detail server-side, return a safe
    // generic message to the client. Never echo raw exception content.
    this.logger.error('Unhandled exception', exception as Error);
    response.status(HttpStatus.INTERNAL_SERVER_ERROR).json({
      error: { code: HttpStatus.INTERNAL_SERVER_ERROR, message: 'Internal server error' },
    });
  }
}
