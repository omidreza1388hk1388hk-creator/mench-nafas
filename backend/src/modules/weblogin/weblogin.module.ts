import { Module } from '@nestjs/common';
import { SessionsModule } from '../sessions/sessions.module';
import { WebLoginService } from './weblogin.service';
import { WebLoginPhoneController, WebLoginPublicController } from './weblogin.controller';
import { PwaController } from './pwa.controller';

@Module({
  imports: [SessionsModule],
  controllers: [WebLoginPublicController, WebLoginPhoneController, PwaController],
  providers: [WebLoginService],
})
export class WebLoginModule {}
