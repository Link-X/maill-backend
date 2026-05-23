package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 演出场次实体类
 */
@Data
public class ShowSession {
    private Long id;
    private Long showId;
    /** 关联场地ID，不为空时座位由场地模板自动复制 */
    private Long roomId;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalSeats;
    private Integer limitPerUser;
    private Integer status;
    /** 座位网格总行数 */
    private Integer rowCount;
    /** 座位网格总列数 */
    private Integer colCount;
    /** 扩展字段 JSON 串（如开售提前N分钟/特殊提示）；不参与 WHERE/索引，约定见前端文档 */
    private String extend;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
