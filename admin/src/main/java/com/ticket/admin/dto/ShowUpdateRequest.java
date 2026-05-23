package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 更新演出
 * 区别于 create：必须传 id；允许更新 status（0=草稿/1=上架/2=下架）
 * createTime / updateTime 仍由后端管理
 */
@Data
public class ShowUpdateRequest {

    @NotNull(message = "id 不能为空")
    private Long id;

    @NotBlank(message = "演出名称不能为空")
    @Size(max = 128, message = "演出名称最长 128 字符")
    private String name;

    private String description;

    private Long categoryId;

    @Size(max = 10, message = "cityCode 最长 10 字符")
    private String cityCode;

    @Size(max = 255, message = "地址最长 255 字符")
    private String address;

    @Size(max = 256, message = "场馆名最长 256 字符")
    private String venue;

    @Size(max = 512, message = "海报 URL 过长")
    private String posterUrl;

    private Map<String, Object> extend;

    /** 上下架状态：0=草稿 1=已上架 2=已下架 */
    @Min(value = 0, message = "status 取值范围 0-2")
    @Max(value = 2, message = "status 取值范围 0-2")
    private Integer status;
}
