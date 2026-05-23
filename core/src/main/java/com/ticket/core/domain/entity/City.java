package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 城市（GB/T 行政区划代码）
 */
@Data
public class City {
    private Long id;
    /** GB/T 行政区划代码，如 110000 北京 */
    private String code;
    private String name;
    private Integer sort;
    /** 0=禁用 1=启用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
