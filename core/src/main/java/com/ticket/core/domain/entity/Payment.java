package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "支付记录")
@Data
public class Payment {
    @Schema(description = "ID", example = "1") private Long id;
    @Schema(description = "订单 ID", example = "1") private Long orderId;
    @Schema(description = "支付单号", example = "PAY-704179544755671040") private String paymentNo;
    @Schema(description = "支付渠道", example = "ALIPAY") private String channel;
    @Schema(description = "支付金额", example = "780.00") private BigDecimal amount;
    @Schema(description = "状态 0=待支付 1=已支付 2=失败", example = "1") private Integer status;
    @Schema(description = "网关交易号", example = "2026052300012345") private String tradeNo;
    @Schema(description = "回调时间") private LocalDateTime callbackTime;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
