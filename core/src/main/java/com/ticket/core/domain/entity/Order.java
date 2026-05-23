package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "订单实体（用户/管理端原始数据；展示用 OrderStatusResponse）")
@Data
public class Order {
    @Schema(description = "订单 ID", example = "1") private Long id;
    @Schema(description = "订单号（雪花 ID）", example = "704179544755671040") private String orderNo;
    @Schema(description = "下单用户 ID", example = "100") private Long userId;
    @Schema(description = "场次 ID", example = "1") private Long sessionId;
    @Schema(description = "订单总金额", example = "780.00") private BigDecimal totalAmount;
    @Schema(description = "状态 0/1/2/3/4/5", example = "1", allowableValues = {"0","1","2","3","4","5"}) private Integer status;
    @Schema(description = "已退款金额累计（多次部分退款会累加）", example = "0.00") private BigDecimal refundAmount;
    @Schema(description = "取消原因 0=用户主动 1=超时；status=2 时有值", example = "1", allowableValues = {"0","1"}) private Integer cancelReason;
    @Schema(description = "支付时间") private LocalDateTime payTime;
    @Schema(description = "过期时间（status=0 时前端可显示倒计时）") private LocalDateTime expireTime;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
