package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.VerifyService;
import com.ticket.user.dto.VerifyQrRequest;
import com.ticket.user.dto.VerifyTicketRequest;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 核销接口：所有方法都需登录，防止外部遍历 ticketNo/qrCode 恶意核销他人票券。
 * 后续应进一步限制为 STAFF 角色，目前仅做"已登录"封堵。
 */
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final VerifyService verifyService;

    public VerifyController(VerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @PostMapping("/qr")
    public Result<?> verifyByQr(@Valid @RequestBody VerifyQrRequest req) {
        return Result.success(verifyService.verifyByQrCode(req.getQrCode()));
    }

    @PostMapping("/ticket")
    public Result<?> verifyByTicketNo(@Valid @RequestBody VerifyTicketRequest req) {
        return Result.success(verifyService.verifyByTicketNo(req.getTicketNo()));
    }
}
