package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "演出场次实体")
@Data
public class ShowSession {
    @Schema(description = "场次 ID", example = "1") private Long id;
    @Schema(description = "演出 ID", example = "1") private Long showId;
    @Schema(description = "场地 ID（不为空时座位由场地模板自动复制）", example = "1") private Long roomId;
    @Schema(description = "场次名称", example = "上海 2026-06-01") private String name;
    @Schema(description = "开始时间", example = "2026-06-01T19:00:00") private LocalDateTime startTime;
    @Schema(description = "结束时间", example = "2026-06-01T22:00:00") private LocalDateTime endTime;
    @Schema(description = "总座位数（由后端从 Room 自动算）", example = "400") private Integer totalSeats;
    @Schema(description = "每人限购数", example = "4") private Integer limitPerUser;
    @Schema(description = "状态 0=未开放 1=销售中 2=已结束 3=已预热", example = "1") private Integer status;
    @Schema(description = "座位网格行数") private Integer rowCount;
    @Schema(description = "座位网格列数") private Integer colCount;
    @Schema(description = "扩展字段 JSON 对象") private Map<String, Object> extend;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
