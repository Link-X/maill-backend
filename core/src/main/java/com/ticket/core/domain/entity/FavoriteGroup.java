package com.ticket.core.domain.entity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "用户收藏分组")
@Data
public class FavoriteGroup {
    @Schema(description = "分组 ID") private Long id;
    @Schema(description = "用户 ID") private Long userId;
    @Schema(description = "分组名") private String name;
    @Schema(description = "排序") private Integer sort;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
