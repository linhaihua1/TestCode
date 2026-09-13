package com.apiweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 首次启动引导配置（默认管理员账号）。
 *
 * <p>对应 {@code application.yml} 的 {@code apiweb.bootstrap.*}：
 * <pre>
 * apiweb:
 *   bootstrap:
 *     enabled: true
 *     admin-username: admin
 *     admin-password: admin@123
 * </pre>
 *
 * <p>生产环境务必通过环境变量 {@code BOOTSTRAP_ADMIN_PASSWORD} 覆盖，或设置
 * {@code enabled=false} 关闭自动创建。
 */
@Data
@Component
@ConfigurationProperties(prefix = "apiweb.bootstrap")
public class BootstrapProperties {

    /** 是否在库中无用户时自动创建默认管理员 */
    private boolean enabled = true;

    /** 默认管理员用户名 */
    private String adminUsername = "admin";

    /** 默认管理员初始密码 */
    private String adminPassword = "admin@123";
}
