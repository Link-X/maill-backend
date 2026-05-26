package com.ticket.core.domain.entity;
import com.ticket.core.domain.vo.ShowVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "用户收藏演出")
@Data
public class UserFavorite {
    @Schema(description = "收藏 ID") private Long id;
    @Schema(description = "用户 ID") private Long userId;
    @Schema(description = "演出 ID") private Long showId;
    @Schema(description = "分组 ID(NULL=未分组)") private Long groupId;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
    /** Service 注入 */
    @Schema(description = "演出展示(join)") private ShowVO show;
}
