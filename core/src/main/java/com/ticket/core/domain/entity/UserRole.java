package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户角色（USER / ADMIN）")
@Data
public class UserRole {
    @Schema(description = "ID") private Long id;
    @Schema(description = "用户 ID", example = "100") private Long userId;
    @Schema(description = "角色", example = "USER", allowableValues = {"USER","ADMIN"}) private String role;
}
