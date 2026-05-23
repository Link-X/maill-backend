package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "取消订单")
@Data
public class CancelOrderRequest {
    @Schema(description = "订单号", example = "704179544755671040", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank private String orderNo;
}
