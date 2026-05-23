package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "用户实体")
@Data
public class User {
    @Schema(description = "用户 ID", example = "100") private Long id;
    @Schema(description = "用户名", example = "testuser") private String username;
    @Schema(description = "手机号") private String phone;
    @Schema(description = "邮箱") private String email;
    @Schema(description = "BCrypt 密码 hash（响应中通常不返回）", accessMode = Schema.AccessMode.WRITE_ONLY) private String passwordHash;
    @Schema(description = "状态 0=禁用 1=正常", example = "1") private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
