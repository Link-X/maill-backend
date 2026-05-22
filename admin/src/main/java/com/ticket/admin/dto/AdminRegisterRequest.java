package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AdminRegisterRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String phone;
    private String email;

    /** 管理员邀请码,与服务端配置 admin.invite-code 比对 */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
