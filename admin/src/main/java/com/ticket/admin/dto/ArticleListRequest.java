package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "资讯列表查询")
@Data
public class ArticleListRequest {
    @Min(0) @Max(2) private Integer status;
    private Long categoryId;
    private String keyword;
    @Min(1) private Integer page = 1;
    @Min(1) @Max(100) private Integer size = 20;
}
