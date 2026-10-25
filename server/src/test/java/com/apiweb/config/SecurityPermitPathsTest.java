package com.apiweb.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证白名单（{@code apiweb.security.permit-paths}）回归测试。
 *
 * <p>白名单漏掉一个入口，表现为该功能「对外一律 401」，且因为不属于崩溃类问题，
 * 很容易在测试里被当成"用例写错"而放过。这里把必须免登录的入口固化为断言：</p>
 *
 * <ul>
 *   <li>{@code /api/v1/auth/login}、{@code /api/v1/auth/register}：未登录时唯一的可用入口</li>
 *   <li>{@code /api/v1/public/**}：报告分享链接，外部用户没有账号</li>
 *   <li>{@code /api/mock/**}：Mock 服务，本身就是给第三方当"假后端"调的</li>
 *   <li>{@code /api/v1/webhook/**}：CI 用独立 token 鉴权，不走 JWT</li>
 * </ul>
 *
 * <p>读取方式：直接从 classpath 的 application.yml 解析默认值（占位符取 {@code :} 后的缺省），
 * 确保校验的是"代码里发出去的那份配置"，而不是测试自己造的值。
 */
class SecurityPermitPathsTest {

    /** application.yml 中 permit-paths 的解析结果（已展开占位符默认值） */
    private static List<String> permitPaths;

    @BeforeAll
    static void loadPermitPaths() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        String raw = null;
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty("apiweb.security.permit-paths");
            if (value != null) {
                raw = String.valueOf(value);
                break;
            }
        }
        assertThat(raw).as("application.yml 必须定义 apiweb.security.permit-paths").isNotNull();

        // ${ENV_KEY:default1,default2} -> 取 default 部分（环境下发时才会有 ENV_KEY）
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", true);
        String resolved = helper.replacePlaceholders(raw, key -> null);
        permitPaths = Arrays.stream(resolved.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Test
    @DisplayName("Mock 服务入口必须免认证，否则 Mock 功能对外全部 401")
    void mockEndpointsMustBePublic() {
        assertThat(permitPaths)
                .as("缺少 /api/mock/** 会导致 Mock 服务无法被外部调用")
                .contains("/api/mock/**");
    }

    @Test
    @DisplayName("登录/注册入口必须免认证")
    void authEntriesMustBePublic() {
        assertThat(permitPaths)
                .contains("/api/v1/auth/login", "/api/v1/auth/register");
    }

    @Test
    @DisplayName("报告分享与 CI Webhook 入口必须免认证")
    void shareAndWebhookEntriesMustBePublic() {
        assertThat(permitPaths)
                .as("分享报告给无账号的外部用户，Webhook 由 CI token 鉴权")
                .contains("/api/v1/public/**")
                .anyMatch(p -> p.startsWith("/api/v1/webhook/"));
    }

    @Test
    @DisplayName("白名单不得放开业务主路径 /api/v1/**（防止误配成全局免认证）")
    void mustNotPermitWholeApiNamespace() {
        assertThat(permitPaths)
                .as("放开 /api/v1/** 等于关闭全部鉴权")
                .doesNotContain("/api/v1/**", "/api/**", "/**");
    }
}
