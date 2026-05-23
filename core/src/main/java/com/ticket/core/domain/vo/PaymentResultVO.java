package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "支付结果")
@Data
public class PaymentResultVO {

    @Schema(description = "支付状态", example = "PAID", allowableValues = {"PAID", "FAILED"})
    private String status;

    @Schema(description = "支付流水号（payment_no，雪花 ID 字符串）", example = "704179544755671041")
    private String paymentNo;

    public static PaymentResultVO of(String status, String paymentNo) {
        PaymentResultVO vo = new PaymentResultVO();
        vo.status = status;
        vo.paymentNo = paymentNo;
        return vo;
    }
}
