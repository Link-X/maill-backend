package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "营收概览（首页 KPI 卡片）")
@Data
public class OverviewVO {

    @Schema(description = "营收金额；仅 status=1/5 订单 totalAmount 之和", example = "128400.00")
    private BigDecimal revenue;

    @Schema(description = "已支付订单数（status=1 或 5）", example = "156")
    private Integer orderCount;

    @Schema(description = "待支付订单数（status=0）", example = "12")
    private Integer pendingOrderCount;

    @Schema(description = "退款总额（order.refund_amount 累计）", example = "2280.00")
    private BigDecimal refundAmount;

    @Schema(description = "退款订单数（status 3/4/5 合计）", example = "6")
    private Integer refundCount;

    @Schema(description = "已售票数；仅 status=1/5 订单的 order_item 行数", example = "412")
    private Integer ticketSoldCount;

    @Schema(description = "已核销票数（ticket.status=1）", example = "358")
    private Integer ticketVerifiedCount;

    @Schema(description = "核销率 = verified / sold，0~1", example = "0.8689")
    private Double verifyRate;

    @Schema(description = "营收环比（与上一等长周期相比），0.15 = 增长 15%，负值代表下降", example = "0.142")
    private Double revenueDeltaPct;

    @Schema(description = "订单数环比", example = "0.084")
    private Double orderCountDeltaPct;
}
