package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "文件上传", description = "上传到 MinIO 对象存储（S3 兼容），返回完整外链 URL；前端拿到 URL 填入 show.posterUrl 等字段")
@RestController
@RequestMapping("/api/admin/upload")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Operation(summary = "上传图片到 MinIO",
            description = "multipart/form-data。支持 jpg/jpeg/png/gif/webp/bmp，单文件 ≤ 10MB。文件按 {dir}/yyyy/MM/dd/{uuid}.{ext} 存储；常用 dir：posters（演出海报）/ rooms（场地图）/ avatars（用户头像）。返回 data.url 直接可访问")
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @Parameter(description = "图片二进制（multipart 表单字段名固定为 file）", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "存储目录前缀，默认 misc；推荐取值 posters / rooms / avatars") @RequestParam(value = "dir", defaultValue = "misc") String dir) {
        String url = storageService.uploadImage(file, dir);
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success(data);
    }
}
