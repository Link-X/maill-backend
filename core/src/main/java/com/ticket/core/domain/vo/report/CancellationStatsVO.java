package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "取消率分析")
@Data
public class CancellationStatsVO {
    @Schema(description = "期间创建的订单总数（所有 status）", example = "200") private Integer createdCount;
    @Schema(description = "超时自动取消（cancel_reason=1）", example = "28") private Integer expiredCancelledCount;
    @Schema(description = "用户主动取消（cancel_reason=0）", example = "12") private Integer userCancelledCount;
    @Schema(description = "取消率 = (expired + user) / created", example = "0.20") private Double cancelRate;
}
