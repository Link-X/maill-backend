package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
@Schema(description = "消息列表查询")
public class MessageListRequest {
    @Min(1) @Max(5) private Integer type;
    @Min(0) @Max(1) private Integer broadcast;
    @Min(1) private Integer page = 1;
    @Min(1) @Max(100) private Integer size = 20;
}
