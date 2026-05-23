package com.ticket.core.domain.vo.report;

import lombok.Data;

@Data
public class CancellationStatsVO {
    /** 期间创建的订单总数（所有 status） */
    private Integer createdCount;
    /** 超时自动取消 status=2, cancel_reason=1 */
    private Integer expiredCancelledCount;
    /** 用户主动取消 status=2, cancel_reason=0 */
    private Integer userCancelledCount;
    /** = (expired + user) / created */
    private Double cancelRate;
}
