package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

/** /api/admin/report/overview 响应 */
@Data
public class OverviewVO {
    /** 营收（status=1/5 订单总额） */
    private BigDecimal revenue;
    /** 已支付订单数 */
    private Integer orderCount;
    /** 待支付订单数 status=0 */
    private Integer pendingOrderCount;
    /** 退款总额 */
    private BigDecimal refundAmount;
    /** 退款订单数 status=3/4/5 */
    private Integer refundCount;
    /** 已售票数 */
    private Integer ticketSoldCount;
    /** 已核销票数 */
    private Integer ticketVerifiedCount;
    /** 核销率 = verified / sold */
    private Double verifyRate;
    /** 营收与上一等长周期对比 */
    private Double revenueDeltaPct;
    /** 订单数与上一等长周期对比 */
    private Double orderCountDeltaPct;
}
