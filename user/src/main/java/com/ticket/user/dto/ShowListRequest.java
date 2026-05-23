package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Schema(description = "用户端演出列表查询；分页 + 多维度筛选")
@Data
public class ShowListRequest {
    @Schema(description = "页码（从 1 开始）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(value = 1) private Integer page;

    @Schema(description = "每页条数 1-100", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(value = 1) @Max(value = 100) private Integer size;

    @Schema(description = "演出名前缀模糊", example = "周杰伦") private String name;
    @Schema(description = "分类 ID 精确", example = "1") private Long categoryId;
    @Schema(description = "城市代码精确", example = "310000") private String cityCode;
    @Schema(description = "场馆名前缀模糊", example = "梅赛德斯") private String venue;
}
