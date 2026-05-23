package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class CategoryUpdateRequest {

    @NotNull(message = "id 不能为空")
    private Long id;

    @Size(max = 64, message = "分类名最长 64 字符")
    private String name;

    private Integer sort;

    @Size(max = 255, message = "icon URL 过长")
    private String icon;

    @Min(value = 0, message = "status 仅支持 0/1")
    @Max(value = 1, message = "status 仅支持 0/1")
    private Integer status;
}
