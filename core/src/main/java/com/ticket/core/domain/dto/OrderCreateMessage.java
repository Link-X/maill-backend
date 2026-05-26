package com.ticket.core.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 异步建单 MQ 消息体。
 *
 * submit 接口同步完成"校验 + 限购 + 锁座 + 预生成 orderNo + 发本消息"后立即返回,
 * 真正的订单 INSERT(以及发超时 MQ)在 {@code OrderCreateConsumer} 异步完成。
 * 失败时由消费者补偿(释放座位 + 退限购 + 标记 FAILED 状态供前端轮询)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateMessage {
    /** 预生成的雪花订单号(字符串,前端用于轮询状态) */
    private String orderNo;
    private Long userId;
    private Long sessionId;
    private List<Long> seatIds;
}
