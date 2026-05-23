package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "演出下的场次列表查询")
@Data
public class SessionListRequest {
    @Schema(description = "演出 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long showId;
    @Schema(description = "页码", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(value = 1) private Integer page;
    @Schema(description = "每页条数 1-100", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(value = 1) @Max(value = 100) private Integer size;
    @Schema(description = "起始时间过滤（startTime ≥ 此值）", example = "2026-06-01T00:00:00") private LocalDateTime startTime;
    @Schema(description = "结束时间过滤", example = "2026-12-31T23:59:59") private LocalDateTime endTime;
    @Schema(description = "状态过滤 0=未开放 1=销售中 2=已结束 3=已预热", example = "1") private Integer status;
}
