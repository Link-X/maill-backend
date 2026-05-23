package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "创建场地（座位布局模板）")
@Data
public class RoomCreateRequest {
    @Schema(description = "场地名称", example = "标准演出场地", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 128) private String name;
    @Schema(description = "所属场馆", example = "国家体育场鸟巢", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 256) private String venue;
    @Schema(description = "座位网格行数；0 表示暂未规划", example = "20") @Min(value = 0) private Integer rowCount;
    @Schema(description = "座位网格列数", example = "20") @Min(value = 0) private Integer colCount;
    @Schema(description = "备注", example = "20行×20列，前 10 行 VIP") @Size(max = 512) private String description;
}
