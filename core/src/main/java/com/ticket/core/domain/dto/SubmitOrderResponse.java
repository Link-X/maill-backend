package com.ticket.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 锁座成功后的同步响应。前端拿到 orderNo 后轮询 /api/order/status 获取真实订单。
 */
@Schema(description = "异步下单的同步响应")
@Data
public class SubmitOrderResponse {
    @Schema(description = "预生成的订单号", example = "704179544755671040") private String orderNo;
    @Schema(description = "当前状态 PROCESSING=建单中(轮询 /api/order/status)") private String status;
}
