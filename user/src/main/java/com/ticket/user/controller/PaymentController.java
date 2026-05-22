package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Payment;
import com.ticket.payment.service.PaymentService;
import com.ticket.user.dto.PaymentRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

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
