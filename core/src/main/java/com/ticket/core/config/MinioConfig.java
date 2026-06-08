package com.ticket.core.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * MinIO 客户端配置:
 * 1. 注册 {@link MinioClient} Bean
 * 2. 应用就绪后(ApplicationReadyEvent)检查 bucket 是否存在,不存在则创建
 * 3. 给 bucket 设置 read-only 公共访问策略,让上传的图片可以直接 URL 访问
 *    (生产环境应换成签名 URL,不直接公开)
 *
 * <p>设计要点:
 *  - 使用 {@link ApplicationReadyEvent} 而非 @PostConstruct,推迟到应用完全启动后再做,
 *    给容器化部署时 MinIO 启动留出窗口;
 *  - 内置重试(最多 5 次,间隔 3s),应对 MinIO 比 admin 晚就绪的场景;
 *  - 推荐配合 docker-compose 的 minio-init 容器使用,基础设施层就把 bucket 弄好,
 *    这里的初始化仅作冷启动兜底。
 *
 * <p>只在配置了 minio.endpoint 的模块(如 admin)生效。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "minio", name = "endpoint")
public class MinioConfig {

    /** 重试次数:覆盖典型的"MinIO 容器晚于 admin 启动"场景 */
    private static final int MAX_RETRIES = 5;
    /** 重试间隔(毫秒) */
    private static final long RETRY_INTERVAL_MS = 3000;

    private final MinioProperties properties;
    private final MinioClient minioClient;

    public MinioConfig(MinioProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Bean
    public MinioClient minioClient() {
        return minioClient;
    }

    /**
     * 应用启动完全就绪后再做 bucket 初始化(带重试)。
     * 失败时记录 ERROR 但不抛出,避免阻塞业务 — 上层应有监控告警捕获该日志。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initBucketOnReady() {
        String bucket = properties.getBucket();
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ensureBucket(bucket);
                ensurePublicReadPolicy(bucket);
                log.info("[MinIO] bucket [{}] 初始化完成 (匿名可读),attempt={}", bucket, attempt);
                return;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    // 已重试到上限仍失败 — ERROR 级别让运维能看到;
                    // 业务可能仍然能用(图片上传 OK 只是不能匿名访问),所以不抛出阻塞启动
                    log.error("[MinIO] bucket [{}] 初始化失败,已重试 {} 次。请检查:" +
                                    "1) MinIO 是否正常运行({}); " +
                                    "2) accessKey/secretKey 是否正确; " +
                                    "3) 推荐 docker-compose 中的 minio-init 容器作为基础设施层兜底。" +
                                    "末次错误:{}",
                            bucket, MAX_RETRIES, properties.getEndpoint(), e.getMessage(), e);
                } else {
                    log.warn("[MinIO] bucket [{}] 初始化第 {} 次失败:{},{}ms 后重试",
                            bucket, attempt, e.getMessage(), RETRY_INTERVAL_MS);
                    sleep(RETRY_INTERVAL_MS);
                }
            }
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[MinIO] bucket [{}] 不存在,已创建", bucket);
        }
    }

    private void ensurePublicReadPolicy(String bucket) throws Exception {
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":\"*\",\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(policy)
                .build());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
