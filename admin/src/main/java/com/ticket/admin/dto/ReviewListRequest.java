package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
@Schema(description = "评价审核列表查询")
public class ReviewListRequest {
    private Long showId;
    @Min(0) @Max(2) private Integer status;
    private String keyword;
    @Min(1) private Integer page = 1;
    @Min(1) @Max(100) private Integer size = 20;
}
