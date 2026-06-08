package com.ticket.core.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 派座模式异步建单 MQ 消息。
 *
 * submit/by-area 同步完成「校验 + 限购 + 扣库存 + 写 task=PENDING + 发本消息」后立即返回 orderNo,
 * 真正的派座 + 建单由 OrderAllocateConsumer 异步执行。
 *
 * 消息体只携带 orderNo,具体派座参数读 seat_allocation_task 表(避免消息体过大且利于幂等)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderAllocateMessage {
    /** 预生成的雪花订单号 */
    private String orderNo;
}
