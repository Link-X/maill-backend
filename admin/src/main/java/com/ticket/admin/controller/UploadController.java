package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.StorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 图片上传：返回 MinIO 可访问 URL，前端写入 show.posterUrl 等字段
 */
@RestController
@RequestMapping("/api/admin/upload")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传图片
     * @param file 表单字段名 file（multipart/form-data）
     * @param dir  目录前缀，默认 misc；常用值: posters / avatars / rooms
     */
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dir", defaultValue = "misc") String dir) {
        String url = storageService.uploadImage(file, dir);
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success(data);
    }
}
