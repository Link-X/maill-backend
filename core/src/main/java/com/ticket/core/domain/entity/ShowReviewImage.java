package com.ticket.core.domain.entity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "评价图片")
@Data
public class ShowReviewImage {
    @Schema(description = "图片 ID") private Long id;
    @Schema(description = "评论 ID") private Long reviewId;
    @Schema(description = "图片 URL") private String url;
    @Schema(description = "排序") private Integer sort;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
