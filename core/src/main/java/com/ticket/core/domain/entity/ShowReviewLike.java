package com.ticket.core.domain.entity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "评价点赞")
@Data
public class ShowReviewLike {
    @Schema(description = "ID") private Long id;
    @Schema(description = "评论 ID") private Long reviewId;
    @Schema(description = "用户 ID") private Long userId;
    @Schema(description = "创建时间") private LocalDateTime createTime;
}
