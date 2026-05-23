package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 对象存储服务：图片上传 / URL 生成
 * - 文件按 {dir}/yyyy/MM/dd/{uuid}.{ext} 组织，避免单目录过载
 * - 返回的 URL 走 publicEndpoint，便于反向代理 / CDN 切换
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "minio", name = "endpoint")
public class StorageService {

    /** 允许的图片扩展名 */
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp"));

    /** 单文件最大 10MB */
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public StorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 上传图片
     *
     * @param file 浏览器上传的 multipart 文件
     * @param dir  目录前缀，例如 "posters" / "avatars"
     * @return 完整可访问 URL
     */
    public String uploadImage(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件大小不能超过 10MB");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "仅支持图片格式: " + IMAGE_EXTENSIONS);
        }

        // 路径：{dir}/yyyy/MM/dd/{uuid}.{ext}
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectName = String.format("%s/%s/%s.%s",
                dir == null || dir.isEmpty() ? "misc" : dir,
                datePath,
                UUID.randomUUID().toString().replace("-", ""),
                ext);

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败: object={}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败: " + e.getMessage());
        }

        return buildPublicUrl(objectName);
    }

    /** 拼接外部访问 URL，优先使用 publicEndpoint */
    private String buildPublicUrl(String objectName) {
        String host = properties.getPublicEndpoint();
        if (host == null || host.isEmpty()) {
            host = properties.getEndpoint();
        }
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host + "/" + properties.getBucket() + "/" + objectName;
    }

    /** 提取小写扩展名 */
    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase();
    }
}
