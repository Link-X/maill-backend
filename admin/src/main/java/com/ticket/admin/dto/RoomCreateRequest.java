package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RoomCreateRequest {

    @NotBlank(message = "场地名称不能为空")
    @Size(max = 128, message = "场地名称最长 128 字符")
    private String name;

    @NotBlank(message = "所属场馆不能为空")
    @Size(max = 256, message = "场馆名最长 256 字符")
    private String venue;

    /** 座位网格行数；0 表示暂未规划 */
    @Min(value = 0, message = "rowCount 不能为负")
    private Integer rowCount;

    @Min(value = 0, message = "colCount 不能为负")
    private Integer colCount;

    @Size(max = 512, message = "备注最长 512 字符")
    private String description;
}
