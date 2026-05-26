package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.vo.UploadResultVO;
import com.ticket.core.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户端文件上传接口：用于评价晒图等 UGC 场景。
 * 与管理端 UploadController 共用同一 StorageService，但限制 dir 的安全取值范围，
 * 防止用户写入到 posters / avatars 等管理用途的目录。
 */
@Tag(name = "用户上传", description = "评价晒图等用户产生内容上传到 MinIO")
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    /** 用户端允许的存储目录白名单 */
    private static final Set<String> ALLOWED_DIRS = new HashSet<>(Arrays.asList("reviews"));

    /** 不在白名单时使用的默认目录 */
    private static final String DEFAULT_DIR = "reviews";

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Operation(summary = "上传图片到 MinIO（用户端）",
            description = "multipart/form-data。dir 只接受白名单值（当前仅 reviews），其他值会被强制改为默认值。返回 data.url 可直接访问。")
    @PostMapping("/image")
    public Result<UploadResultVO> uploadImage(
            @Parameter(description = "图片二进制（multipart 表单字段名固定为 file）", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "存储目录前缀，仅允许 reviews；其他值会被默认改为 reviews")
            @RequestParam(value = "dir", required = false) String dir) {
        String safeDir = (dir != null && ALLOWED_DIRS.contains(dir)) ? dir : DEFAULT_DIR;
        String url = storageService.uploadImage(file, safeDir);
        return Result.success(UploadResultVO.of(url));
    }
}
