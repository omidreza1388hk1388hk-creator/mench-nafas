import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  ParseIntPipe,
  ParseUUIDPipe,
  Post,
  Put,
  Req,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
import { Type } from 'class-transformer';
import { IsInt, IsOptional, IsString, Max, MaxLength, Min } from 'class-validator';
import { toAttachmentDto } from './attachment.dto';
import { ChunkedUploadService } from './chunked-upload.service';
import { ConversationsService } from '../conversations/conversations.service';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

class InitChunkedUploadDto {
  @IsString() @MaxLength(255) originalFilename!: string;
  @IsString() @MaxLength(100) mimeType!: string;
  @Type(() => Number) @IsInt() @Min(1) totalSize!: number;
  @Type(() => Number) @IsInt() @Min(1) chunkSize!: number;
  @IsOptional() @Type(() => Number) @IsInt() @Min(1) durationMs?: number;
  @IsOptional() @Type(() => Number) @IsInt() @Min(1) @Max(20000) widthPx?: number;
  @IsOptional() @Type(() => Number) @IsInt() @Min(1) @Max(20000) heightPx?: number;
}

const CHUNK_MULTER = { storage: memoryStorage(), limits: { fileSize: 9 * 1024 * 1024 } };

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/uploads')
export class ChunkedUploadController {
  constructor(
    private readonly uploads: ChunkedUploadService,
    private readonly conversationsService: ConversationsService,
  ) {}

  @Post()
  async init(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Body() dto: InitChunkedUploadDto) {
    await this.conversationsService.assertMember(conversationId, req.user.sub);
    return this.uploads.init({ userId: req.user.sub, conversationId, ...dto });
  }

  @Get(':uploadId')
  status(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Param('uploadId', ParseUUIDPipe) uploadId: string) {
    return this.uploads.status(req.user.sub, conversationId, uploadId);
  }

  @Put(':uploadId/chunks/:index')
  @UseInterceptors(FileInterceptor('chunk', CHUNK_MULTER))
  putChunk(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('uploadId', ParseUUIDPipe) uploadId: string,
    @Param('index', ParseIntPipe) index: number,
    @UploadedFile() file?: Express.Multer.File,
  ) {
    if (!file) throw new BadRequestException('No chunk part');
    return this.uploads.putChunk(req.user.sub, conversationId, uploadId, index, file.buffer);
  }

  @Put(':uploadId/thumbnail')
  @UseInterceptors(FileInterceptor('thumbnail', CHUNK_MULTER))
  async putThumbnail(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('uploadId', ParseUUIDPipe) uploadId: string,
    @UploadedFile() file?: Express.Multer.File,
  ) {
    if (!file) throw new BadRequestException('No thumbnail part');
    await this.uploads.putThumbnail(req.user.sub, conversationId, uploadId, file.buffer);
    return { ok: true };
  }

  @Post(':uploadId/complete')
  async complete(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('uploadId', ParseUUIDPipe) uploadId: string,
  ) {
    await this.conversationsService.assertMember(conversationId, req.user.sub);
    return toAttachmentDto(await this.uploads.complete(req.user.sub, conversationId, uploadId));
  }
}
