package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "保存资讯(id 为空=新增,否则=更新)")
@Data
public class ArticleSaveRequest {
    private Long id;
    @NotNull private Long categoryId;
    @NotBlank @Size(max = 200) private String title;
    @Size(max = 500) private String summary;
    @NotBlank private String content;
    @Size(max = 500) private String coverUrl;
    private Long artistId;
    @Size(max = 50) private String author;
    @Min(0) @Max(2) private Integer status;
}
