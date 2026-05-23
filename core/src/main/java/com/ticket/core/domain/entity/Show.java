package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 演出实体类
 */
@Data
public class Show {
    private Long id;
    private String name;
    private String description;
    /** 关联 category.id */
    private Long categoryId;
    /** 关联 city.code (GB/T 行政区划代码) */
    private String cityCode;
    /** 详细地址 */
    private String address;
    private String posterUrl;
    /** 场馆名 */
    private String venue;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
