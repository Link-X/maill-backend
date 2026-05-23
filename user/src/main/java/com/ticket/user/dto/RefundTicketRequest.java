package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "单票退款")
@Data
public class RefundTicketRequest {
    @Schema(description = "订单号", example = "704179544755671040", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank private String orderNo;
    @Schema(description = "票券编号（8 位友好票号）", example = "GH37KX2P", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank private String ticketNo;
}
