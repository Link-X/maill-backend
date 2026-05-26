package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "保存艺人(id 为空=新增,否则=更新)")
@Data
public class ArtistSaveRequest {
    @Schema(description = "艺人 ID,新增传 null") private Long id;
    @Schema(description = "本名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 100) private String name;
    @Schema(description = "艺名") @Size(max = 100) private String stageName;
    @Schema(description = "头像 URL") @Size(max = 500) private String avatarUrl;
    @Schema(description = "性别 0=保密 1=男 2=女", example = "0")
    @Min(0) @Max(2) private Integer gender;
    @Schema(description = "国籍/地区") @Size(max = 50) private String nationality;
    @Schema(description = "标签,逗号分隔") @Size(max = 500) private String tags;
    @Schema(description = "简介短文本") @Size(max = 500) private String bio;
    @Schema(description = "富文本详介") private String description;
    @Schema(description = "社交链接 JSON 字符串") private String socialLinks;
    @Schema(description = "状态 0=下架 1=上架", example = "1")
    @Min(0) @Max(1) private Integer status;
}
