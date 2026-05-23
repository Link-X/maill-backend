package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Payment;
import com.ticket.payment.service.PaymentService;
import com.ticket.user.dto.PaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@Tag(name = "支付", description = "Mock 支付网关：调用 PaymentService 模拟支付，成功后更新订单状态、通过 RabbitMQ Fanout 异步触发票券生成 / 库存同步 / 通知")
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "支付订单", description = "幂等：只有 status=0 待支付的订单才会被处理；其它状态返回当前支付记录。仅订单本人可操作。返回 { status: PAID|FAILED, paymentNo }")
    @PostMapping("/create")
    public Result<Map<String, Object>> createPayment(@Valid @RequestBody PaymentRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Payment payment = paymentService.processPayment(req.getOrderNo(), userId, req.getChannel());
        return Result.success(Map.of(
                "status", payment.getStatus() == 1 ? "PAID" : "FAILED",
                "paymentNo", payment.getPaymentNo()
        ));
    }
}
