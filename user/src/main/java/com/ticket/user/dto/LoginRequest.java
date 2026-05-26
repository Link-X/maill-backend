package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "用户登录")
@Data
public class LoginRequest {
    @Schema(description = "用户名", example = "testuser", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空") private String username;
    @Schema(description = "密码", example = "test12345", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空") private String password;
}
