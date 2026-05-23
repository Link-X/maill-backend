package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "支付订单")
@Data
public class PaymentRequest {
    @Schema(description = "订单号", example = "704179544755671040", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "订单号不能为空") private String orderNo;
    @Schema(description = "支付渠道（mock 网关用于区分，可选）", example = "ALIPAY") private String channel;
}
