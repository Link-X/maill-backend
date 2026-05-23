package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 统计报表通用时间窗口参数：range 与 startTime/endTime 二选一，同传时自定义时间覆盖 range。
 */
@Schema(description = "统计报表通用时间窗口；range 与 startTime/endTime 二选一，同传时自定义时间覆盖")
@Data
public class TimeRangeQuery {

    @Schema(description = "时间窗口：1d / 7d / 30d / 90d；不传则默认 30d", example = "30d",
            allowableValues = {"1d", "7d", "30d", "90d"})
    private String range;

    @Schema(description = "自定义起始时间（ISO 8601 LocalDateTime），与 endTime 同传时覆盖 range",
            example = "2026-05-01T00:00:00")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @Schema(description = "自定义结束时间", example = "2026-05-31T23:59:59")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
