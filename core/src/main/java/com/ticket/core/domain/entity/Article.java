package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "资讯")
@Data
public class Article {
    @Schema(description = "资讯 ID") private Long id;
    @Schema(description = "分类 ID") private Long categoryId;
    @Schema(description = "标题") private String title;
    @Schema(description = "摘要(列表展示)") private String summary;
    @Schema(description = "富文本内容(HTML)") private String content;
    @Schema(description = "封面 URL") private String coverUrl;
    @Schema(description = "关联艺人 ID(可空)") private Long artistId;
    @Schema(description = "作者") private String author;
    @Schema(description = "浏览数") private Integer viewCount;
    @Schema(description = "状态 0=草稿 1=已发布 2=已下架") private Integer status;
    @Schema(description = "发布时间") private LocalDateTime publishedAt;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
    /** Service 层 join 后注入 */
    @Schema(description = "分类(join 注入)") private ArticleCategory category;
    @Schema(description = "艺人(join 注入)") private Artist artist;
}
