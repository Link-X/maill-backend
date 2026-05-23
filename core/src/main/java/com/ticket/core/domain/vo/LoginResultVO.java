package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "登录 / 注册成功返回；前端拿 token 后续接口 Header 携带 Bearer ${token}")
@Data
public class LoginResultVO {

    @Schema(description = "JWT 访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "用户 ID（雪花 ID）", example = "1761000000000000001")
    private Long userId;

    @Schema(description = "用户角色列表", example = "[\"USER\"]")
    private List<String> roles;

    public static LoginResultVO of(String token, Long userId, List<String> roles) {
        LoginResultVO vo = new LoginResultVO();
        vo.token = token;
        vo.userId = userId;
        vo.roles = roles;
        return vo;
    }
}
