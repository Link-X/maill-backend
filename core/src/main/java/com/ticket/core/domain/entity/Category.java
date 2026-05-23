package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 演出分类
 */
@Data
public class Category {
    private Long id;
    private String name;
    private Integer sort;
    private String icon;
    /** 0=禁用 1=启用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
