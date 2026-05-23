package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "场地实体（座位布局模板载体）")
@Data
public class Room {
    @Schema(description = "场地 ID", example = "1") private Long id;
    @Schema(description = "场地名称", example = "标准演出场地") private String name;
    @Schema(description = "所属场馆", example = "国家体育场鸟巢") private String venue;
    @Schema(description = "座位网格行数", example = "20") private Integer rowCount;
    @Schema(description = "座位网格列数", example = "20") private Integer colCount;
    @Schema(description = "备注") private String description;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
