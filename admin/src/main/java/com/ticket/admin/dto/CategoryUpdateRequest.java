package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "更新分类")
@Data
public class CategoryUpdateRequest {
    @Schema(description = "分类 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long id;
    @Schema(description = "分类名（改名时校验重名）", example = "演唱会") @Size(max = 64) private String name;
    @Schema(description = "排序", example = "5") private Integer sort;
    @Schema(description = "图标 URL") @Size(max = 255) private String icon;
    @Schema(description = "状态 0=禁用 1=启用", example = "1", allowableValues = {"0","1"})
    @Min(value = 0) @Max(value = 1) private Integer status;
}
