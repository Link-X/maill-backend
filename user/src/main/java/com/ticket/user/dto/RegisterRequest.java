package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Schema(description = "用户注册")
@Data
public class RegisterRequest {
    @Schema(description = "用户名 3-32 字符，仅字母/数字/下划线", example = "testuser", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "^[a-zA-Z0-9_]+$") private String username;

    @Schema(description = "密码 8-64 字符，必须含字母和数字", example = "test12345", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") private String password;

    @Schema(description = "手机号（可选）", example = "13800000001") private String phone;
    @Schema(description = "邮箱（可选）", example = "test@example.com") private String email;
}
