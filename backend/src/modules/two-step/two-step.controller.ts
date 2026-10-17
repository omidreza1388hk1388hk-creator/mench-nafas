import { Body, Controller, Delete, Get, HttpCode, Put, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { TwoStepService } from './two-step.service';
import { DisableTwoStepDto, SetTwoStepDto } from './dto/two-step.dto';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@UseGuards(JwtAuthGuard)
@Controller('two-step')
export class TwoStepController {
  constructor(private readonly twoStep: TwoStepService) {}

  @Get()
  status(@Req() req: any) {
    return this.twoStep.status(req.user.sub);
  }

  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Put()
  async set(@Req() req: any, @Body() dto: SetTwoStepDto) {
    await this.twoStep.set(req.user.sub, dto.password, dto.hint, dto.currentPassword);
    return this.twoStep.status(req.user.sub);
  }

  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Delete()
  @HttpCode(200)
  async disable(@Req() req: any, @Body() dto: DisableTwoStepDto) {
    await this.twoStep.disable(req.user.sub, dto.password);
    return { enabled: false, hint: null };
  }
}
