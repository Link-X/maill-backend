package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "举报处理")
public class ReportHandleRequest {
    @NotNull private Long reportId;
    @NotBlank private String action; // keep | delete
}
