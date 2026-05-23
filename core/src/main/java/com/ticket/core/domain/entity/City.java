package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "城市（GB/T 行政区划代码）；由 schema.sql seed 30 个主要城市")
@Data
public class City {
    @Schema(description = "城市 ID", example = "1") private Long id;
    @Schema(description = "GB/T 行政区划代码", example = "110000") private String code;
    @Schema(description = "城市名", example = "北京") private String name;
    @Schema(description = "排序", example = "1") private Integer sort;
    @Schema(description = "状态 0=禁用 1=启用", example = "1", allowableValues = {"0","1"}) private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
