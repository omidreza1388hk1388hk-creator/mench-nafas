import {
  Body,
  Controller,
  Get,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  Req,
  Res,
  UploadedFiles,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileFieldsInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
import type { Response } from 'express';
import { AttachmentsService } from './attachments.service';
import { toAttachmentDto } from './attachment.dto';
import { UploadAttachmentDto } from './dto/upload-attachment.dto';
import { ConversationsService } from '../conversations/conversations.service';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

// Coarse first-line memory cap on the multipart parser itself — the
// precise, configurable limit (MAX_ATTACHMENT_SIZE_BYTES) is enforced in
// AttachmentsService against the actual configured value. This layer
// exists so an oversized upload is rejected before its bytes are even
// fully buffered into memory, not just after. Bumped from the original
// 40MB for video clips, which are realistically larger than any image or
// voice note.
const MULTER_HARD_CAP_BYTES = 80 * 1024 * 1024;

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/attachments')
export class AttachmentsController {
  constructor(
    private readonly attachmentsService: AttachmentsService,
    private readonly conversationsService: ConversationsService,
  ) {}

  @Post()
  @UseInterceptors(
    FileFieldsInterceptor(
      [
        { name: 'file', maxCount: 1 },
        // Optional — only present for video uploads, a client-extracted
        // frame (see AttachmentsService's doc comment on why this isn't
        // generated server-side).
        { name: 'thumbnail', maxCount: 1 },
      ],
      { storage: memoryStorage(), limits: { fileSize: MULTER_HARD_CAP_BYTES } },
    ),
  )
  async upload(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @UploadedFiles() files: { file?: Express.Multer.File[]; thumbnail?: Express.Multer.File[] },
    @Body() dto: UploadAttachmentDto,
  ) {
    const file = files.file?.[0];
    if (!file) {
      throw new NotFoundException('No file part in the upload');
    }

    await this.conversationsService.assertMember(conversationId, req.user.sub);

    const attachment = await this.attachmentsService.upload({
      uploaderId: req.user.sub,
      conversationId,
      originalFilename: file.originalname,
      mimeType: file.mimetype,
      buffer: file.buffer,
      durationMs: dto.durationMs ?? null,
      thumbnailBuffer: files.thumbnail?.[0]?.buffer ?? null,
      clientReportedWidthPx: dto.widthPx ?? null,
      clientReportedHeightPx: dto.heightPx ?? null,
    });

    return toAttachmentDto(attachment);
  }
}

@UseGuards(JwtAuthGuard)
@Controller('attachments')
export class AttachmentContentController {
  constructor(
    private readonly attachmentsService: AttachmentsService,
    private readonly conversationsService: ConversationsService,
  ) {}

  @Get(':id/content')
  async getContent(
    @Req() req: any,
    @Param('id', ParseUUIDPipe) id: string,
    @Res() res: Response,
  ): Promise<void> {
    const attachment = await this.attachmentsService.findById(id);
    if (!attachment) throw new NotFoundException('Attachment not found');

    // Authorization on every content fetch, not just at upload time: only
    // members of the conversation this attachment belongs to can read it
    // back — the same 404-not-403 policy as everywhere else (spec 34).
    await this.conversationsService.assertMember(attachment.conversation_id, req.user.sub);

    const buffer = await this.attachmentsService.getContent(attachment.storage_key);
    res.set({
      'Content-Type': attachment.mime_type,
      'Content-Length': buffer.length.toString(),
      'Cache-Control': 'private, max-age=31536000, immutable',
    });
    res.send(buffer);
  }

  @Get(':id/thumbnail')
  async getThumbnail(
    @Req() req: any,
    @Param('id', ParseUUIDPipe) id: string,
    @Res() res: Response,
  ): Promise<void> {
    const attachment = await this.attachmentsService.findById(id);
    if (!attachment || !attachment.thumbnail_storage_key) {
      throw new NotFoundException('No thumbnail for this attachment');
    }

    await this.conversationsService.assertMember(attachment.conversation_id, req.user.sub);

    const buffer = await this.attachmentsService.getContent(attachment.thumbnail_storage_key);
    res.set({
      'Content-Type': 'image/webp',
      'Content-Length': buffer.length.toString(),
      'Cache-Control': 'private, max-age=31536000, immutable',
    });
    res.send(buffer);
  }
}
