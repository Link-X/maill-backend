package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "首页 Banner / 运营位实体")
@Data
public class Banner {
    @Schema(description = "Banner ID", example = "1") private Long id;
    @Schema(description = "后台备注,前端可不展示", example = "618 活动 banner") private String title;
    @Schema(description = "图片 URL", example = "http://localhost:9000/image/banners/xxx.jpg") private String imageUrl;
    @Schema(description = "跳转类型 0=无 1=演出 2=艺人 3=资讯 4=外链", example = "1") private Integer linkType;
    @Schema(description = "跳转目标:演出/艺人/资讯 ID 或外链 URL", example = "42") private String linkTarget;
    @Schema(description = "排序,越小越靠前", example = "10") private Integer sort;
    @Schema(description = "定时上架时间,null=立即生效") private LocalDateTime startAt;
    @Schema(description = "定时下架时间,null=永久") private LocalDateTime endAt;
    @Schema(description = "状态 0=下架 1=上架", example = "1", allowableValues = {"0","1"}) private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
