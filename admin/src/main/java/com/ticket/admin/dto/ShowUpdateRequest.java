package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

@Schema(description = "更新演出；必须传 id；允许更新 status（0/1/2）")
@Data
public class ShowUpdateRequest {

    @Schema(description = "演出 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空") private Long id;

    @Schema(description = "演出名称", example = "周杰伦嘉年华世界巡回演唱会", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "演出名称不能为空") @Size(max = 128) private String name;

    @Schema(description = "演出描述") private String description;
    @Schema(description = "分类 ID", example = "1") private Long categoryId;
    @Schema(description = "城市代码", example = "310000") @Size(max = 10) private String cityCode;
    @Schema(description = "详细地址") @Size(max = 255) private String address;
    @Schema(description = "场馆名") @Size(max = 256) private String venue;
    @Schema(description = "海报 URL") @Size(max = 512) private String posterUrl;
    @Schema(description = "扩展 JSON 对象") private Map<String, Object> extend;

    @Schema(description = "状态 0=草稿 1=已上架 2=已下架", example = "1", allowableValues = {"0","1","2"})
    @Min(value = 0) @Max(value = 2) private Integer status;
}
