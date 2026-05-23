package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "场次实时座位监控")
@Data
public class DashboardVO {

    @Schema(description = "场次 ID", example = "1")
    private Long sessionId;

    @Schema(description = "总座位数", example = "1000")
    private Integer totalSeats;

    @Schema(description = "实时可售座位数（来自 Redis Set）", example = "742")
    private Long availableCount;

    @Schema(description = "已售座位数 = totalSeats - availableCount", example = "258")
    private Long soldCount;

    public static DashboardVO of(Long sessionId, Integer totalSeats, long availableCount) {
        DashboardVO vo = new DashboardVO();
        vo.sessionId = sessionId;
        vo.totalSeats = totalSeats;
        vo.availableCount = availableCount;
        vo.soldCount = totalSeats - availableCount;
        return vo;
    }
}
