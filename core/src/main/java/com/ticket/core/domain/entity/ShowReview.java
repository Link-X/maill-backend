package com.ticket.core.domain.entity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "演出评价(一级评论或二级回复)")
@Data
public class ShowReview {
    @Schema(description = "评论 ID") private Long id;
    @Schema(description = "演出 ID") private Long showId;
    @Schema(description = "用户 ID") private Long userId;
    @Schema(description = "订单 ID(已观看模式必填)") private Long orderId;
    @Schema(description = "NULL=一级评论;否则=所属一级评论 ID") private Long parentId;
    @Schema(description = "二级回复时@的目标用户 ID") private Long replyToUserId;
    @Schema(description = "内容") private String content;
    @Schema(description = "评分 1-5,仅一级评论") private Integer rating;
    @Schema(description = "点赞数") private Integer likeCount;
    @Schema(description = "回复数(仅一级维护)") private Integer replyCount;
    @Schema(description = "0=正常 1=举报中 2=被隐藏") private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
    /** join 注入 */
    @Schema(description = "图片列表") private List<ShowReviewImage> images;
    @Schema(description = "评论人(简化:仅 userId / username)") private String username;
    @Schema(description = "回复目标用户名(二级回复)") private String replyToUsername;
    /** 当前查看者是否已点赞(Service 注入) */
    @Schema(description = "当前查看者是否已点赞") private Boolean liked;
}
