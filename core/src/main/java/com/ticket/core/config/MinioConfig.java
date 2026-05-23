package com.ticket.core.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * MinIO 客户端配置：
 * 1. 注册 {@link MinioClient} Bean
 * 2. 启动时检查 bucket 是否存在，不存在则创建
 * 3. 给 bucket 设置 read-only 公共访问策略，让上传的图片可以直接 URL 访问
 *    （生产环境应换成签名 URL，不直接公开）
 *
 * 只在配置了 minio.endpoint 的模块（如 admin）生效，user/payment 等不需要存储能力的模块不会加载。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "minio", name = "endpoint")
public class MinioConfig {

    private final MinioProperties properties;

    public MinioConfig(MinioProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /**
     * 启动时确保 bucket 存在并对外可读
     */
    @PostConstruct
    public void initBucket() {
        try {
            MinioClient client = minioClient();
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                log.info("MinIO bucket [{}] 创建成功", properties.getBucket());
            }
            // 公共读取策略：所有对象通过 URL 直接可访问
            String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                    + "\"Principal\":\"*\",\"Action\":[\"s3:GetObject\"],"
                    + "\"Resource\":[\"arn:aws:s3:::" + properties.getBucket() + "/*\"]}]}";
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(properties.getBucket())
                    .config(policy)
                    .build());
        } catch (Exception e) {
            // 启动期连不上 MinIO 不应阻塞整个应用，只记录告警
            log.warn("MinIO bucket 初始化失败：{}，请确认 MinIO 服务是否启动", e.getMessage());
        }
    }
}
