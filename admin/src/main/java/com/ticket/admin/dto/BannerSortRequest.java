package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Banner 排序请求,按数组顺序重写 sort")
@Data
public class BannerSortRequest {
    @Schema(description = "按目标顺序排列的 Banner ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty private List<Long> orderedIds;
}
