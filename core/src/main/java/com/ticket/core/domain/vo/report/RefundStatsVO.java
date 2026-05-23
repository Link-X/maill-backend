package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "退款指标")
@Data
public class RefundStatsVO {
    @Schema(description = "营收（status=1/5）", example = "128400.00") private BigDecimal totalRevenue;
    @Schema(description = "退款总额（order.refund_amount 累加）", example = "2280.00") private BigDecimal refundAmount;
    @Schema(description = "退款率 = refundAmount / totalRevenue", example = "0.0178") private Double refundRate;
    @Schema(description = "全退订单数（status=4）", example = "3") private Integer fullRefundCount;
    @Schema(description = "部分退订单数（status=5）", example = "2") private Integer partialRefundCount;
    @Schema(description = "退款中订单数（status=3）", example = "1") private Integer refundingCount;
}
