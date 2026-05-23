package com.ticket.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置
 * - endpoint: 服务端地址（如 http://localhost:9000）
 * - accessKey / secretKey: 凭据
 * - bucket: 默认 bucket 名称
 * - publicEndpoint: 对外暴露 URL 时使用的 host（默认与 endpoint 相同；用于反向代理 / CDN 场景）
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String publicEndpoint;
}
