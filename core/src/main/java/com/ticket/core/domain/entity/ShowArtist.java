package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "演出-艺人关联")
@Data
public class ShowArtist {
    @Schema(description = "关联 ID") private Long id;
    @Schema(description = "演出 ID") private Long showId;
    @Schema(description = "艺人 ID") private Long artistId;
    @Schema(description = "角色") private String role;
    @Schema(description = "排序") private Integer sort;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
