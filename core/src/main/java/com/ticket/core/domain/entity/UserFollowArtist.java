package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户关注艺人")
@Data
public class UserFollowArtist {
    @Schema(description = "关联 ID") private Long id;
    @Schema(description = "用户 ID") private Long userId;
    @Schema(description = "艺人 ID") private Long artistId;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
