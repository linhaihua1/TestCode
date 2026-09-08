package com.apiweb.config;

import com.apiweb.security.AesGcm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import jakarta.annotation.PostConstruct;

import java.security.SecureRandom;

/**
 * AES 主密钥配置。
 *
 * <h3>读取顺序</h3>
 * <ol>
 *   <li>系统属性 {@code apiweb.aes.master-key}</li>
 *   <li>环境变量 {@code APIWEB_AES_MASTER_KEY}</li>
 *   <li>Spring 配置 {@code apiweb.aes.master-key}</li>
 * </ol>
 *
 * <p>未显式配置时,使用随机生成的 32 字节密钥（仅用于本地开发）。
 * 生产环境务必通过配置中心 / Vault 注入。
 */
@Slf4j
@Configuration
public class AesConfig {

    @Value("${apiweb.aes.master-key:}")
    private String configuredKey;

    private final Environment env;

    public AesConfig(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        String key = firstNonBlank(
                System.getProperty("apiweb.aes.master-key"),
                System.getenv("APIWEB_AES_MASTER_KEY"),
                configuredKey,
                env.getProperty("apiweb.aes.master-key"));
        if (key == null || key.isBlank()) {
            // 本地开发场景自动生成随机密钥,日志醒目提示
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            AesGcm.setMasterKey(random);
            log.warn("⚠️ 未配置 AES 主密钥,已自动生成随机密钥（重启后旧 SECRET 变量将无法解密）。生产环境务必设置 apiweb.aes.master-key");
        } else {
            AesGcm.setMasterKeyFromString(key);
            log.info("✅ AES 主密钥加载成功（来自配置）");
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}