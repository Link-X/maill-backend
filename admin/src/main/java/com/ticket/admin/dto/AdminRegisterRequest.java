package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Schema(description = "管理员注册（需邀请码）")
@Data
public class AdminRegisterRequest {
    @Schema(description = "用户名 3-32 字符，仅字母/数字/下划线", example = "ops_alice")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    @Schema(description = "密码 12-64 字符，必须含大小写字母、数字、特殊符号", example = "Strong@Pass123")
    @NotBlank(message = "密码不能为空")
    @Size(min = 12, max = 64, message = "管理员密码长度需在 12-64 字符之间")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\",.<>?/]).+$",
            message = "管理员密码必须包含大写字母、小写字母、数字、特殊符号")
    private String password;

    @Schema(description = "手机号（可选）", example = "13800000001")
    private String phone;

    @Schema(description = "邮箱（可选）", example = "ops@example.com")
    private String email;

    @Schema(description = "管理员邀请码（与服务端 ADMIN_INVITE_CODE 配置比对）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
