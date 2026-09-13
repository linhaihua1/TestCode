package com.apiweb.service;

import com.apiweb.config.MinioConfig;
import com.apiweb.config.OssProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 对象存储服务：超过阈值（默认 1MB）的请求/响应体转存到对象存储，数据库只存引用路径。
 *
 * <p>支持两种实现：
 * <ul>
 *   <li><b>MinIO 模式</b>（生产）：通过 {@link MinioClient} 写入 MinIO，引用形如
 *       {@code minio://{bucket}/{objectKey}}</li>
 *   <li><b>本地文件系统模式</b>（dev/test，{@code minio.enabled=false}）：写入本地目录，
 *       引用形如 {@code localfs://{objectKey}}</li>
 * </ul>
 * 业务代码调用统一接口，对底层无感知；切换实现无需改业务逻辑。
 *
 * <p>阈值 / 本地根目录来自 {@link OssProperties}，bucket 来自 {@link MinioConfig}，
 * 引用 scheme 为内部协议常量（读写必须一致，不对外开放配置）。
 */
@Slf4j
@Service
public class OssService {

    /** MinIO 引用 scheme（内部协议常量） */
    private static final String SCHEME_MINIO = "minio://";
    /** 本地文件系统引用 scheme（内部协议常量） */
    private static final String SCHEME_LOCALFS = "localfs://";
    /** 转存对象的 MIME 类型 */
    private static final String CONTENT_TYPE_JSON = "application/json";
    /** 本地降级目录默认名（位于 ${java.io.tmpdir} 下） */
    private static final String LOCAL_DIR_NAME = "apiweb-oss";

    private final MinioClient minioClient;        // 可能为 null（localfs 模式）
    private final String bucket;
    private final long largeBodyThreshold;
    private final boolean enabled;                // 是否启用 MinIO
    private final Path localRoot;                 // localfs 模式下的根目录

    public OssService(@Value("${minio.enabled:true}") boolean enabled,
                      @Autowired(required = false) MinioClient minioClient,
                      @Autowired(required = false) MinioConfig minioConfig,
                      OssProperties ossProperties) {
        this.enabled = enabled;
        this.minioClient = enabled ? minioClient : null;
        // bucket 由配置提供（minio.bucket），MinIO 禁用时留空即可
        this.bucket = minioConfig == null ? "" : minioConfig.getBucket();
        this.largeBodyThreshold = ossProperties.getLargeBodyThreshold();
        this.localRoot = resolveLocalRoot(ossProperties.getLocalRoot());
    }

    /**
     * 解析本地降级根目录：优先取配置值，未配置时落到系统临时目录下，避免硬编码绝对路径。
     */
    private static Path resolveLocalRoot(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        String tmp = System.getProperty("java.io.tmpdir");
        return Paths.get(tmp, LOCAL_DIR_NAME);
    }

    /**
     * 启动时确保本地存储根目录存在（仅 localfs 模式）
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            try {
                Files.createDirectories(localRoot);
                log.warn("[OssService] MinIO 已禁用（minio.enabled=false），使用本地文件系统作为对象存储：{}",
                        localRoot.toAbsolutePath());
            } catch (IOException e) {
                throw new IllegalStateException("无法创建本地对象存储目录: " + localRoot, e);
            }
        } else {
            log.info("[OssService] MinIO 已启用（bucket={}）", bucket);
        }
    }

    /**
     * 内容是否超过转存阈值（供调用方判断是否只保留引用）。
     */
    public boolean exceedsThreshold(String content) {
        return content != null && content.length() > largeBodyThreshold;
    }

    /**
     * 若内容超过阈值则上传到对象存储并返回引用路径；否则原样返回内容。
     * 引用格式：minio 模式 {@code minio://{bucket}/{key}}；localfs 模式 {@code localfs://{key}}
     */
    public String putIfLarge(String content, String objectKey) {
        if (!exceedsThreshold(content)) {
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
                    .contentType(CONTENT_TYPE_JSON)
                    .build());
        }
        return SCHEME_MINIO + bucket + "/" + objectKey;
    }

    private String putToLocalFs(String content, String objectKey) throws IOException {
        Path target = localRoot.resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return SCHEME_LOCALFS + objectKey;
    }

    public boolean isOffloaded(String value) {
        if (value == null) return false;
        return value.startsWith(SCHEME_MINIO) || value.startsWith(SCHEME_LOCALFS);
    }

    /**
     * 根据引用读取原始内容
     */
    public String get(String reference) {
        if (!isOffloaded(reference)) {
            return reference;
        }
        try {
            if (reference.startsWith(SCHEME_MINIO)) {
                String objectKey = reference.substring((SCHEME_MINIO + bucket + "/").length());
                try (var in = minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                String objectKey = reference.substring(SCHEME_LOCALFS.length());
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
            if (reference.startsWith(SCHEME_MINIO)) {
                String objectKey = reference.substring((SCHEME_MINIO + bucket + "/").length());
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket).object(objectKey).build());
            } else {
                String objectKey = reference.substring(SCHEME_LOCALFS.length());
                Files.deleteIfExists(localRoot.resolve(objectKey));
            }
        } catch (Exception e) {
            log.warn("对象存储删除失败: {}", e.getMessage());
        }
    }
}
