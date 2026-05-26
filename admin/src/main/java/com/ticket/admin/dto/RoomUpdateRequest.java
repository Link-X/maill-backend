package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "更新场地")
@Data
public class RoomUpdateRequest {
    @Schema(description = "场地 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long id;
    @Schema(description = "场地名称", example = "标准演出场地", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 128) private String name;
    @Schema(description = "所属场馆", example = "国家体育场鸟巢", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 256) private String venue;
    @Schema(description = "行数", example = "20") @Min(value = 0) private Integer rowCount;
    @Schema(description = "列数", example = "20") @Min(value = 0) private Integer colCount;
    @Schema(description = "备注") @Size(max = 512) private String description;
}
