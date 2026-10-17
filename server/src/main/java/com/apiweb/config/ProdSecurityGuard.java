package com.apiweb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 生产环境安全启动检查（fail-fast）。
 *
 * <p>仅在<b>非 localdev</b> profile 下生效：任一安全关键配置使用默认值/空值时，
 * 直接抛异常阻止启动——避免「密钥为空自动随机」或「默认弱密码」的实例
 * 在生产环境悄悄上线。所有问题都会在日志中给出<b>明确的修复指引</b>。</p>
 *
 * <h3>检查项</h3>
 * <ol>
 *   <li>{@code apiweb.jwt.secret}：为空时启动失败（为空时 JwtUtil 会生成随机密钥，
 *       每次重启所有 token 失效，生产不可接受）</li>
 *   <li>{@code apiweb.bootstrap.admin-password}：仍为默认值 {@code admin@123} 时启动失败</li>
 * </ol>
 */
@Slf4j
@Configuration
@Profile("!localdev")
public class ProdSecurityGuard implements InitializingBean {

    /** bootstrap 管理员密码的默认值（与 application.yml 保持一致，仅用于比对） */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin@123";

    @Value("${apiweb.jwt.secret:}")
    private String jwtSecret;

    @Value("${apiweb.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException(
                    "安全启动检查失败：JWT 密钥未配置。请通过环境变量 JWT_SECRET 注入"
                            + "（HS256，至少 32 字节），例如：JWT_SECRET=$(openssl rand -hex 32)");
        }
        if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            throw new IllegalStateException(
                    "安全启动检查失败：管理员密码仍为默认值 admin@123。"
                            + "请通过环境变量 BOOTSTRAP_ADMIN_PASSWORD 注入强密码，"
                            + "或设置 BOOTSTRAP_ADMIN_ENABLED=false 关闭自动建号");
        }
        log.info("生产安全启动检查通过：JWT 密钥已注入，管理员密码已非默认值");
    }
}
