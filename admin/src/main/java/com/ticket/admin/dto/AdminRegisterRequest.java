package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class AdminRegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    /** 管理员密码要求更严:至少 12 字符,需包含大写字母、小写字母、数字、特殊符号 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 12, max = 64, message = "管理员密码长度需在 12-64 字符之间")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\",.<>?/]).+$",
            message = "管理员密码必须包含大写字母、小写字母、数字、特殊符号")
    private String password;

    private String phone;
    private String email;

    /** 管理员邀请码,与服务端配置 admin.invite-code 比对 */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
