package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "票号核销")
@Data
public class VerifyTicketRequest {
    @Schema(description = "8 位友好票号（排除 O/0/I/1）", example = "GH37KX2P", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank private String ticketNo;
}
