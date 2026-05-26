package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建分类")
@Data
public class CategoryCreateRequest {
    @Schema(description = "分类名（全表唯一）", example = "演唱会", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 64) private String name;
    @Schema(description = "排序，越小越靠前；默认 0", example = "10") private Integer sort;
    @Schema(description = "图标 URL（可选）", example = "https://example.com/icons/talk.png") @Size(max = 255) private String icon;
    @Schema(description = "状态 0=禁用 1=启用（默认 1）", example = "1", allowableValues = {"0","1"})
    @Min(value = 0) @Max(value = 1) private Integer status;
}
