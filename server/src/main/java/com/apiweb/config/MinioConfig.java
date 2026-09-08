package com.apiweb.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置：超过 1MB 的调试记录/报告请求体、响应体存对象存储，数据库只存引用路径。
 * <p>
 * 通过 {@code minio.enabled} 控制 Bean 创建。当值为 false（dev/test 环境无 MinIO）时，
 * 本配置类完全跳过，OssService 自动降级为本地文件系统实现。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinioConfig {

    private boolean enabled = true;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket [{}] 已创建", bucket);
            } else {
                log.info("MinIO bucket [{}] 已存在", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO 初始化失败（中间件未启动？大对象存储将不可用）: {}", e.getMessage());
        }
        return client;
    }
}