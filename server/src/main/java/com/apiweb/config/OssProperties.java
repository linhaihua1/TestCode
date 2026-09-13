package com.apiweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储（OSS / MinIO）配置。
 *
 * <p>对应 {@code application.yml} 的 {@code apiweb.oss.*}：
 * <pre>
 * apiweb:
 *   oss:
 *     large-body-threshold: 1048576        # 超过该字节数的请求/响应体转存对象存储
 *     local-root: ${OSS_LOCAL_ROOT:}       # MinIO 不可用时的本地降级目录（留空则用临时目录）
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "apiweb.oss")
public class OssProperties {

    /** 请求/响应体超过该大小（字节）时转存对象存储，默认 1MB */
    private long largeBodyThreshold = 1_048_576L;

    /** 本地文件系统降级模式的根目录；为空时使用 ${java.io.tmpdir}/apiweb-oss */
    private String localRoot = "";
}
