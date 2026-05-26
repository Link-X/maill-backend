package com.ticket.core.domain.entity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "评价举报")
@Data
public class ShowReviewReport {
    @Schema(description = "举报 ID") private Long id;
    @Schema(description = "评论 ID") private Long reviewId;
    @Schema(description = "举报人") private Long reporterId;
    @Schema(description = "原因") private String reason;
    @Schema(description = "0=待处理 1=已处理-保留 2=已处理-删除") private Integer status;
    @Schema(description = "admin 处理人") private Long handlerId;
    @Schema(description = "处理时间") private LocalDateTime handledAt;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
