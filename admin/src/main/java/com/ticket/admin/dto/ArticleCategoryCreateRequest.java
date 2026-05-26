package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "创建资讯分类")
@Data
public class ArticleCategoryCreateRequest {
    @NotBlank @Size(max = 50) private String name;
    @Min(0) private Integer sort;
    @Min(0) @Max(1) private Integer status;
}
