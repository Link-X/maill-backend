package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "用户购买行为")
@Data
public class UserStatsVO {
    @Schema(description = "总买家数（去重，status=1/5）", example = "320") private Integer totalBuyers;
    @Schema(description = "复购买家数（同期 >=2 次）", example = "48") private Integer repeatBuyers;
    @Schema(description = "复购率 0~1", example = "0.15") private Double repeatRate;
    @Schema(description = "客单价 = revenue / orderCount", example = "823.00") private BigDecimal avgOrderValue;
    @Schema(description = "平均座位数 = ticketCount / orderCount", example = "2.6") private Double avgSeatsPerOrder;
}
