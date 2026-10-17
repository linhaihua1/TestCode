package com.apiweb.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证与项目 CRUD 主链路集成测试（真实 MySQL + Redis + Spring 上下文）。
 *
 * <p>运行条件：本机 MySQL(3306) / Redis(6379) 可用，使用 localdev profile。
 * 由 failsafe 插件在 {@code mvn verify} 阶段执行（命名约定 *IT）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=localdev")
class AuthFlowIT {

    private static final String BASE = "/api/v1";

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Map> r) {
        return r.getBody();
    }

    @Test
    @DisplayName("登录 -> me -> 错误密码 1006 -> 项目 CRUD 主链路")
    void loginAndProjectCrudFlow() {
        // 1. 管理员登录
        ResponseEntity<Map> login = rest.exchange(BASE + "/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "admin", "password", "admin@123"),
                        jsonHeaders()), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> loginBody = body(login);
        assertThat(loginBody.get("code")).isEqualTo(0);
        Map<String, Object> data = (Map<String, Object>) loginBody.get("data");
        String token = (String) data.get("token");
        assertThat(token).isNotBlank();

        // 2. 当前用户信息
        ResponseEntity<Map> me = rest.exchange(BASE + "/auth/me", HttpMethod.GET,
                new HttpEntity<>(auth(token)), Map.class);
        Map<String, Object> meData = (Map<String, Object>) body(me).get("data");
        assertThat(meData.get("username")).isEqualTo("admin");

        // 3. 错误密码 -> 1006（用不存在的账号，避免污染 admin 失败计数）
        ResponseEntity<Map> badLogin = rest.exchange(BASE + "/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "it_probe_nonexist", "password", "x123456"),
                        jsonHeaders()), Map.class);
        assertThat(body(badLogin).get("code")).isEqualTo(1006);

        // 4. 无 token 访问受保护接口 -> 1002
        ResponseEntity<Map> noAuth = rest.getForEntity(BASE + "/projects", Map.class);
        assertThat(body(noAuth).get("code")).isEqualTo(1002);

        // 5. 项目 CRUD
        String name = "IT-集成验证-" + System.currentTimeMillis();
        ResponseEntity<Map> created = rest.exchange(BASE + "/projects", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "description", "it"), auth(token)), Map.class);
        assertThat(body(created).get("code")).isEqualTo(0);
        String pid = (String) ((Map<String, Object>) body(created).get("data")).get("id");

        ResponseEntity<Map> got = rest.exchange(BASE + "/projects/" + pid, HttpMethod.GET,
                new HttpEntity<>(auth(token)), Map.class);
        assertThat(((Map<String, Object>) body(got).get("data")).get("name")).isEqualTo(name);

        ResponseEntity<Map> updated = rest.exchange(BASE + "/projects/" + pid, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", name + "-改", "description", "it2"), auth(token)), Map.class);
        assertThat(body(updated).get("code")).isEqualTo(0);

        ResponseEntity<Map> deleted = rest.exchange(BASE + "/projects/" + pid, HttpMethod.DELETE,
                new HttpEntity<>(auth(token)), Map.class);
        assertThat(body(deleted).get("code")).isEqualTo(0);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
