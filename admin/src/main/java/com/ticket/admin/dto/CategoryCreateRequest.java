package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class CategoryCreateRequest {

    @NotBlank(message = "分类名不能为空")
    @Size(max = 64, message = "分类名最长 64 字符")
    private String name;

    /** 排序，默认 0；越小越靠前 */
    private Integer sort;

    @Size(max = 255, message = "icon URL 过长")
    private String icon;

    /** 0=禁用 1=启用，默认 1 */
    @Min(value = 0, message = "status 仅支持 0/1")
    @Max(value = 1, message = "status 仅支持 0/1")
    private Integer status;
}
