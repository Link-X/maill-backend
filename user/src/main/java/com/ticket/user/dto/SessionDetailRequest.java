package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Schema(description = "场次详情查询")
@Data
public class SessionDetailRequest {
    @Schema(description = "场次 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场次ID不能为空") private Long sessionId;
}
