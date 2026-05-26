package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Schema(description = "Banner 上下架请求")
@Data
public class BannerStatusRequest {
    @Schema(description = "Banner ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long id;
    @Schema(description = "目标状态 0=下架 1=上架", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(0) @Max(1) private Integer status;
}
