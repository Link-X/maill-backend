package com.ticket.core.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 演出展示 VO：Show 字段 + 冗余 categoryName，避免前端再查一次分类表
 */
@Data
public class ShowVO {
    private Long id;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private String cityCode;
    private String cityName;
    private String address;
    private String posterUrl;
    private String venue;
    private Integer status;
    /** 扩展字段 JSON 串，原样透传给前端 */
    private String extend;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
