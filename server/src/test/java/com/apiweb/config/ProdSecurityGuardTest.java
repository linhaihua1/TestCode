package com.apiweb.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生产安全启动检查单测：关键配置缺失/使用默认值时必须 fail-fast。
 */
class ProdSecurityGuardTest {

    private ProdSecurityGuard newGuard(String secret, String adminPassword) {
        ProdSecurityGuard guard = new ProdSecurityGuard();
        ReflectionTestUtils.setField(guard, "jwtSecret", secret);
        ReflectionTestUtils.setField(guard, "adminPassword", adminPassword);
        return guard;
    }

    @Test
    @DisplayName("JWT_SECRET 为空 -> 启动失败并提示注入方式")
    void shouldFailWhenSecretEmpty() {
        assertThatThrownBy(() -> newGuard("", "strong-pass").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("管理员密码为默认值 -> 启动失败")
    void shouldFailWhenAdminPasswordIsDefault() {
        assertThatThrownBy(() -> newGuard("a".repeat(32), "admin@123").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    @DisplayName("密钥与密码均合法 -> 检查通过")
    void shouldPassWithProperConfig() {
        assertThatCode(() -> newGuard("a".repeat(32), "S7rong!Passw0rd").afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
