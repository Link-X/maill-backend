package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 通用分页返回；字段名 total/list 与原 Map.of("total", t, "list", l) 保持完全一致，
 * 序列化后前端无感知。
 */
@Schema(description = "分页返回结构")
@Data
public class PageVO<T> {

    @Schema(description = "总记录数", example = "128")
    private long total;

    @Schema(description = "当前页数据")
    private List<T> list;

    public PageVO() {}

    public PageVO(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }

    public static <T> PageVO<T> of(long total, List<T> list) {
        return new PageVO<>(total, list);
    }
}
