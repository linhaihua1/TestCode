package com.apiweb.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 对象存储服务：超过阈值（默认 1MB）的请求/响应体转存 MinIO，数据库只存引用路径。
 * 引用格式：minio://{bucket}/{objectKey}
 */
@Slf4j
@Service
public class OssService {

    private final MinioClient minioClient;
    private final String bucket;
    private final long largeBodyThreshold;

    public OssService(MinioClient minioClient,
                      @Value("${minio.bucket}") String bucket,
                      @Value("${apiweb.oss.large-body-threshold}") long largeBodyThreshold) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.largeBodyThreshold = largeBodyThreshold;
    }

    /**
     * 若内容超过阈值则上传 MinIO 并返回引用路径；否则原样返回内容（表示无需转存）。
     * 返回值语义由调用方结合 needsOffload() 判断。
     */
    public String putIfLarge(String content, String objectKey) {
        if (content == null || content.length() <= largeBodyThreshold) {
            return content;
        }
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (var in = new ByteArrayInputStream(bytes)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(in, bytes.length, -1)
                        .contentType("application/json")
                        .build());
            }
            return "minio://" + bucket + "/" + objectKey;
        } catch (Exception e) {
            log.error("MinIO 上传失败，降级为数据库直存: {}", e.getMessage());
            return content;
        }
    }

    public boolean isOffloaded(String value) {
        return value != null && value.startsWith("minio://");
    }

    public String get(String reference) {
        if (!isOffloaded(reference)) {
            return reference;
        }
        String objectKey = reference.substring(("minio://" + bucket + "/").length());
        try (var in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("MinIO 下载失败: {}", e.getMessage());
            return null;
        }
    }

    public void delete(String reference) {
        if (!isOffloaded(reference)) {
            return;
        }
        String objectKey = reference.substring(("minio://" + bucket + "/").length());
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.warn("MinIO 删除失败: {}", e.getMessage());
        }
    }
}
