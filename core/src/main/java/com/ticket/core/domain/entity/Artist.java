package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "艺人实体")
@Data
public class Artist {
    @Schema(description = "艺人 ID") private Long id;
    @Schema(description = "本名") private String name;
    @Schema(description = "艺名") private String stageName;
    @Schema(description = "头像 URL") private String avatarUrl;
    @Schema(description = "性别 0=保密 1=男 2=女") private Integer gender;
    @Schema(description = "国籍/地区") private String nationality;
    @Schema(description = "标签,逗号分隔") private String tags;
    @Schema(description = "简介短文本") private String bio;
    @Schema(description = "富文本详介") private String description;
    @Schema(description = "社交链接 JSON 字符串,如 {\"weibo\":\"\",\"instagram\":\"\",\"x\":\"\"}") private String socialLinks;
    @Schema(description = "粉丝数") private Integer followCount;
    @Schema(description = "关联演出数") private Integer showCount;
    @Schema(description = "状态 0=下架 1=上架") private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
