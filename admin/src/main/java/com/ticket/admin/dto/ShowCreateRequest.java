package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "创建演出；status 由后端固定为 1，id/createTime 由后端生成")
@Data
public class ShowCreateRequest {

    @Schema(description = "演出名称", example = "周杰伦嘉年华世界巡回演唱会", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "演出名称不能为空") @Size(max = 128, message = "演出名称最长 128 字符")
    private String name;

    @Schema(description = "演出描述", example = "周杰伦全球巡演上海站，经典金曲全回归")
    private String description;

    @Schema(description = "关联 category.id（先调 /api/admin/category/list 取）", example = "1")
    private Long categoryId;

    @Schema(description = "城市代码 city.code（GB/T 行政区划代码）", example = "310000")
    @Size(max = 10, message = "cityCode 最长 10 字符") private String cityCode;

    @Schema(description = "详细地址", example = "浦东新区世博大道 1200 号")
    @Size(max = 255, message = "地址最长 255 字符") private String address;

    @Schema(description = "场馆名", example = "上海梅赛德斯奔驰文化中心")
    @Size(max = 256, message = "场馆名最长 256 字符") private String venue;

    @Schema(description = "海报 URL（建议先调 /api/admin/upload/image 拿到）", example = "http://localhost:9000/image/posters/2026/05/23/xxx.jpg")
    @Size(max = 512, message = "海报 URL 过长") private String posterUrl;

    @Schema(description = "扩展 JSON 对象（duration/ageLimit/refundRule 等），后端原样存库，不参与 WHERE/索引",
            example = "{\"duration\":180,\"ageLimit\":\"6+\",\"refundRule\":\"开演前24小时可退\"}")
    private Map<String, Object> extend;

    @Schema(description = "评价模式 0=无评价 1=所有可评 2=仅已观看;默认 1",
            example = "1", allowableValues = {"0","1","2"})
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(2)
    private Integer reviewMode;

    @Schema(description = "开售时间(null=立即可购)") private java.time.LocalDateTime openSaleTime;

    @Schema(description = "关联艺人 ID 列表(可空)") private java.util.List<Long> artistIds;

    @Schema(description = "艺人角色映射 {artistId: '主演'}", example = "{\"1\":\"主演\"}")
    private java.util.Map<Long, String> artistRoles;
}
