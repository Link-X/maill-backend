package com.ticket.core.domain.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 */
@Data
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long sessionId;
    private BigDecimal totalAmount;
    private Integer status;
    /** 已退款金额累计；多次部分退款会累加 */
    private BigDecimal refundAmount;
    /** 取消原因 0=用户主动 1=超时自动；status=2 时才有值 */
    private Integer cancelReason;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
