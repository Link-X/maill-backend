package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 创建演出
 * - status 由后端固定为 1（已上架），不接受前端传入
 * - id / createTime / updateTime 由后端生成，不接受前端传入
 */
@Data
public class ShowCreateRequest {

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

    /** 扩展字段（JSON 对象，前端直接传 object），原样存库，不参与 WHERE/索引 */
    private Map<String, Object> extend;
}
