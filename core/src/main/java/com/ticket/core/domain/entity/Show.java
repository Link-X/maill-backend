package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "演出实体（管理端读写；用户端展示用 ShowVO）")
@Data
public class Show {
    @Schema(description = "演出 ID", example = "1") private Long id;
    @Schema(description = "演出名称", example = "周杰伦嘉年华") private String name;
    @Schema(description = "演出描述") private String description;
    @Schema(description = "关联 category.id", example = "1") private Long categoryId;
    @Schema(description = "关联 city.code（GB/T 行政区划代码）", example = "310000") private String cityCode;
    @Schema(description = "详细地址") private String address;
    @Schema(description = "海报 URL") private String posterUrl;
    @Schema(description = "场馆名") private String venue;
    @Schema(description = "状态 0=草稿 1=已上架 2=已下架", example = "1") private Integer status;
    @Schema(description = "评价模式 0=无评价 1=所有可评 2=仅已观看", example = "1", allowableValues = {"0","1","2"})
    private Integer reviewMode;
    @Schema(description = "平均评分(冗余,DB 维护)") private java.math.BigDecimal avgRating;
    @Schema(description = "评价数(冗余,DB 维护)") private Integer reviewCount;
    @Schema(description = "开售时间,用于开售提醒触发") private LocalDateTime openSaleTime;
    @Schema(description = "扩展字段 JSON 对象") private Map<String, Object> extend;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;

    /** Service 层 join show_artist 后注入,非 DB 列 */
    @Schema(description = "关联艺人(查询时由 Service 注入)")
    private java.util.List<com.ticket.core.domain.entity.Artist> artists;
}
