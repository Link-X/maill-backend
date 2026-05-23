package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "文件上传结果")
@Data
public class UploadResultVO {

    @Schema(description = "上传后的可访问完整 URL", example = "https://minio.example.com/ticket/posters/2026/05/23/xxx.jpg")
    private String url;

    public static UploadResultVO of(String url) {
        UploadResultVO vo = new UploadResultVO();
        vo.url = url;
        return vo;
    }
}
