package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "创建场次；status 后端固定为 0；totalSeats/rowCount/colCount 由后端根据 roomId 自动计算")
@Data
public class SessionCreateRequest {

    @Schema(description = "演出 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "showId 不能为空") private Long showId;

    @Schema(description = "场地 ID（推荐传，后端自动复制座位模板和默认价格；不传则需自行 /seat/batch + /area/save）", example = "1")
    private Long roomId;

    @Schema(description = "场次名称", example = "上海 2026-06-01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 128) private String name;

    @Schema(description = "开始时间", example = "2026-06-01T19:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2026-06-01T22:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private LocalDateTime endTime;

    @Schema(description = "每人限购数", example = "4") @Min(value = 1) private Integer limitPerUser;

    @Schema(description = "扩展 JSON 对象（如 preSaleLeadMinutes/notice）", example = "{\"preSaleLeadMinutes\":30}")
    private Map<String, Object> extend;
}
