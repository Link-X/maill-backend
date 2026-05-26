package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Schema(description = "开售时间;不传则定时任务可立即把场次流转为销售中(取决于是否在 end_time 之前)",
            example = "2026-06-01T10:00:00")
    private LocalDateTime openSaleTime;

    @Schema(description = "扩展 JSON 对象（如 preSaleLeadMinutes/notice）", example = "{\"preSaleLeadMinutes\":30}")
    private Map<String, Object> extend;
}
