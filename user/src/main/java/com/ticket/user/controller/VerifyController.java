package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.VerifyService;
import com.ticket.user.dto.VerifyQrRequest;
import com.ticket.user.dto.VerifyTicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "入场核验", description = "二维码 / 票号核销。所有方法都需登录（防止外部遍历恶意核销他人票券），后续可进一步限制为 STAFF 角色。原子 UPDATE 防并发重复核销")
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final VerifyService verifyService;

    public VerifyController(VerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @Operation(summary = "二维码核销", description = "扫描票券二维码完成入场。原子 UPDATE 防并发重复核销")
    @PostMapping("/qr")
    public Result<?> verifyByQr(@Valid @RequestBody VerifyQrRequest req) {
        return Result.success(verifyService.verifyByQrCode(req.getQrCode()));
    }

    @Operation(summary = "票号核销", description = "替代扫码：输入 8 位友好票号（排除 O/0/I/1）完成入场")
    @PostMapping("/ticket")
    public Result<?> verifyByTicketNo(@Valid @RequestBody VerifyTicketRequest req) {
        return Result.success(verifyService.verifyByTicketNo(req.getTicketNo()));
    }
}
