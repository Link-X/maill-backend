package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "演出分类实体")
@Data
public class Category {
    @Schema(description = "分类 ID", example = "1") private Long id;
    @Schema(description = "分类名（全表唯一）", example = "演唱会") private String name;
    @Schema(description = "排序，越小越靠前", example = "10") private Integer sort;
    @Schema(description = "图标 URL") private String icon;
    @Schema(description = "状态 0=禁用 1=启用", example = "1", allowableValues = {"0","1"}) private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
