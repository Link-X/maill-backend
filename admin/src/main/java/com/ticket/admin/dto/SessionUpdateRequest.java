package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "更新场次；不允许更新 status（开售用 /publish）/ totalSeats / rowCount / colCount / showId / roomId")
@Data
public class SessionUpdateRequest {
    @Schema(description = "场次 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long id;
    @Schema(description = "场次名称", example = "上海 2026-06-01（已更新）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 128) private String name;
    @Schema(description = "开始时间", example = "2026-06-01T19:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private LocalDateTime startTime;
    @Schema(description = "结束时间", example = "2026-06-01T22:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private LocalDateTime endTime;
    @Schema(description = "每人限购数", example = "4") @Min(value = 1) private Integer limitPerUser;
    @Schema(description = "扩展 JSON 对象") private Map<String, Object> extend;
}
