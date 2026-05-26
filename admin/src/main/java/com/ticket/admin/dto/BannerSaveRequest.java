package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "保存 Banner 请求(id 为空=新增,否则=更新)")
@Data
public class BannerSaveRequest {
    @Schema(description = "Banner ID,新增传 null") private Long id;
    @Schema(description = "后台备注", example = "618 活动")
    @Size(max = 100) private String title;
    @Schema(description = "图片 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 500) private String imageUrl;
    @Schema(description = "跳转类型 0=无 1=演出 2=艺人 3=资讯 4=外链", example = "1")
    @Min(0) @Max(4) private Integer linkType;
    @Schema(description = "跳转目标")
    @Size(max = 500) private String linkTarget;
    @Schema(description = "排序")
    @Min(0) private Integer sort;
    @Schema(description = "定时上架时间(null=立即)") private LocalDateTime startAt;
    @Schema(description = "定时下架时间(null=永久)") private LocalDateTime endAt;
    @Schema(description = "状态 0=下架 1=上架", example = "0")
    @Min(0) @Max(1) private Integer status;
}
