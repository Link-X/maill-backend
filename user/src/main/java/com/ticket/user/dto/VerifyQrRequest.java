package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "二维码核销")
@Data
public class VerifyQrRequest {
    @Schema(description = "票券二维码内容（UUID）", example = "b1c8a5e0-7e4c-4f9a-bf83-3c5e6c9d8a01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank private String qrCode;
}
