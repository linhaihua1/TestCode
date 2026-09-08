package com.apiweb.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 对象存储服务：超过阈值（默认 1MB）的请求/响应体转存到对象存储，数据库只存引用路径。
 * <p>
 * 支持两种实现：
 * <ul>
 *   <li><b>MinIO 模式</b>（生产）：通过 {@link MinioClient} 写入 MinIO，引用形如 "minio://{bucket}/{objectKey}"</li>
 *   <li><b>本地文件系统模式</b>（dev/test，minio.enabled=false）：直接写到本地目录，引用形如 "localfs://{objectKey}"</li>
 * </ul>
 * 业务代码调用统一接口，对底层无感知；切换实现无需改业务逻辑。
 */
@Slf4j
@Service
public class OssService {

    private final MinioClient minioClient;        // 可能为 null（localfs 模式）
    private final String bucket;
    private final long largeBodyThreshold;
    private final boolean enabled;                // 是否启用 MinIO
    private final Path localRoot;                 // localfs 模式下的根目录

    public OssService(@Value("${minio.enabled:true}") boolean enabled,
                      @org.springframework.beans.factory.annotation.Autowired(required = false) MinioClient minioClient,
                      @Value("${minio.bucket:apiweb}") String bucket,
                      @Value("${apiweb.oss.large-body-threshold:1048576}") long largeBodyThreshold,
                      @Value("${apiweb.oss.local-root:C:/dev/apiweb-oss}") String localRoot) {
        this.enabled = enabled;
        this.minioClient = enabled ? minioClient : null;
        this.bucket = bucket;
        this.largeBodyThreshold = largeBodyThreshold;
        this.localRoot = Paths.get(localRoot);
    }

    /**
     * 启动时确保本地存储根目录存在（仅 localfs 模式）
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            try {
                Files.createDirectories(localRoot);
                log.warn("[OssService] MinIO 已禁用（minio.enabled=false），使用本地文件系统作为对象存储：{}", localRoot.toAbsolutePath());
            } catch (IOException e) {
                throw new IllegalStateException("无法创建本地对象存储目录: " + localRoot, e);
            }
        } else {
            log.info("[OssService] MinIO 已启用（bucket={}）", bucket);
        }
    }

    /**
     * 若内容超过阈值则上传到对象存储并返回引用路径；否则原样返回内容。
     * 引用格式：minio 模式 "minio://{bucket}/{key}"；localfs 模式 "localfs://{key}"
     */
    public String putIfLarge(String content, String objectKey) {
        if (content == null || content.length() <= largeBodyThreshold) {
            return content;
        }
        try {
            if (enabled) {
                return putToMinio(content, objectKey);
            } else {
                return putToLocalFs(content, objectKey);
            }
        } catch (Exception e) {
            log.error("对象存储写入失败，降级为数据库直存: {}", e.getMessage());
            return content;
        }
    }

    private String putToMinio(String content, String objectKey) throws Exception {
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
    }

    private String putToLocalFs(String content, String objectKey) throws IOException {
        Path target = localRoot.resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return "localfs://" + objectKey;
    }

    public boolean isOffloaded(String value) {
        if (value == null) return false;
        return value.startsWith("minio://") || value.startsWith("localfs://");
    }

    /**
     * 根据引用读取原始内容
     */
    public String get(String reference) {
        if (!isOffloaded(reference)) {
            return reference;
        }
        try {
            if (reference.startsWith("minio://")) {
                String objectKey = reference.substring(("minio://" + bucket + "/").length());
                try (var in = minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                String objectKey = reference.substring("localfs://".length());
                return Files.readString(localRoot.resolve(objectKey), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("对象存储读取失败: {}", e.getMessage());
            return null;
        }
    }

    public void delete(String reference) {
        if (!isOffloaded(reference)) {
            return;
        }
        try {
            if (reference.startsWith("minio://")) {
                String objectKey = reference.substring(("minio://" + bucket + "/").length());
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket).object(objectKey).build());
            } else {
                String objectKey = reference.substring("localfs://".length());
                Files.deleteIfExists(localRoot.resolve(objectKey));
            }
        } catch (Exception e) {
            log.warn("对象存储删除失败: {}", e.getMessage());
        }
    }
}