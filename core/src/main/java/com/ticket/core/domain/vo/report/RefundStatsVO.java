package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RefundStatsVO {
    private BigDecimal totalRevenue;
    private BigDecimal refundAmount;
    private Double refundRate;
    private Integer fullRefundCount;      // status=4
    private Integer partialRefundCount;   // status=5
    private Integer refundingCount;       // status=3
}
