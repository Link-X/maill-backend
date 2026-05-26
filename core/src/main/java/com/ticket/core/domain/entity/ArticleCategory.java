package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "资讯分类")
@Data
public class ArticleCategory {
    @Schema(description = "分类 ID") private Long id;
    @Schema(description = "分类名（全表唯一）") private String name;
    @Schema(description = "排序") private Integer sort;
    @Schema(description = "状态 0=下架 1=上架") private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
