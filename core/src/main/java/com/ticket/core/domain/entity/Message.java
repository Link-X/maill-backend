package com.ticket.core.domain.entity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "消息主表")
@Data
public class Message {
    @Schema(description = "消息 ID") private Long id;
    @Schema(description = "类型 1=订单 2=开售 3=系统 4=互动 5=关注动态") private Integer type;
    @Schema(description = "标题") private String title;
    @Schema(description = "内容") private String content;
    @Schema(description = "跳转类型 0=无 1=演出 2=艺人 3=资讯 4=订单 5=URL") private Integer linkType;
    @Schema(description = "跳转目标") private String linkTarget;
    @Schema(description = "0=单发 1=广播") private Integer broadcast;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
