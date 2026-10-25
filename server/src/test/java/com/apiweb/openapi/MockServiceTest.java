package com.apiweb.openapi;

import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.openapi.mock.MockEngine;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MockService} 匹配规则测试。
 *
 * <p>保护点：Mock 必须"确定性命中"。历史数据允许出现同 {@code method + path} 的多条接口，
 * 如果退化回 {@code findFirst()}，命中结果将取决于数据库返回顺序，用户会看到
 * "改了 Mock 响应却一直返回旧内容"且无从排查。</p>
 */
class MockServiceTest {

    private ApiDefinitionMapper apiMapper;
    private MockService mockService;

    @BeforeEach
    void setUp() {
        apiMapper = mock(ApiDefinitionMapper.class);
        mockService = new MockService(apiMapper, mock(MockEngine.class));
    }

    /** 构造一条接口定义 */
    private ApiDefinitionEntity api(String id, String method, String path, Instant createdAt,
                                    String mockResponse) {
        ApiDefinitionEntity e = new ApiDefinitionEntity();
        e.setId(id);
        e.setProjectId("p1");
        e.setMethod(method);
        e.setPath(path);
        e.setMockEnabled(true);
        e.setMockResponse(mockResponse);
        e.setCreatedAt(createdAt);
        return e;
    }

    @SuppressWarnings("unchecked")
    private void given(List<ApiDefinitionEntity> candidates) {
        when(apiMapper.selectList(any(Wrapper.class))).thenReturn(candidates);
    }

    @Test
    @DisplayName("同路径多条时取最新创建的一条（新配置覆盖旧配置）")
    void shouldPickNewestAmongDuplicates() {
        Instant old = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        // 故意把旧的排在前面，模拟数据库无序返回
        given(List.of(
                api("old", "GET", "/mock/demo", old, "{\"v\":1}"),
                api("new", "GET", "/mock/demo", now, "{\"v\":2}")));

        ApiDefinitionEntity matched = mockService.match("p1", "GET", "/mock/demo");

        assertThat(matched).isNotNull();
        assertThat(matched.getId()).as("应命中最新创建的那条").isEqualTo("new");
    }

    @Test
    @DisplayName("精确路径优先于 Ant 模板，不被 /users/{id} 抢走")
    void exactPathShouldBeatTemplate() {
        Instant old = Instant.parse("2026-06-01T00:00:00Z");
        Instant now = Instant.parse("2026-07-01T00:00:00Z");
        // 模板比精确路径更新，仍应优先精确匹配
        given(List.of(
                api("exact", "GET", "/users/login", old, "{\"v\":\"exact\"}"),
                api("tpl", "GET", "/users/{id}", now, "{\"v\":\"tpl\"}")));

        ApiDefinitionEntity matched = mockService.match("p1", "GET", "/users/login");

        assertThat(matched).isNotNull();
        assertThat(matched.getId()).isEqualTo("exact");
    }

    @Test
    @DisplayName("无精确路径时回落到模板匹配")
    void shouldFallbackToTemplate() {
        given(List.of(api("tpl", "GET", "/users/{id}", Instant.now(), "{\"v\":\"tpl\"}")));

        ApiDefinitionEntity matched = mockService.match("p1", "GET", "/users/42");

        assertThat(matched).isNotNull();
        assertThat(matched.getId()).isEqualTo("tpl");
    }

    @Test
    @DisplayName("路径不匹配时返回 null")
    void shouldReturnNullWhenPathNotMatch() {
        // 方法过滤在 SQL 层完成（selectList 的 wrapper），单测里只验证路径匹配这一层
        given(List.of(api("a", "POST", "/users/login", Instant.now(), "{}")));

        assertThat(mockService.match("p1", "POST", "/other")).isNull();
        assertThat(mockService.match("p1", "POST", "/users/login/extra")).isNull();
    }

    @Test
    @DisplayName("候选为空时返回 null，不抛异常")
    void shouldReturnNullWhenNoCandidates() {
        given(List.of());

        assertThat(mockService.match("p1", "GET", "/anything")).isNull();
    }
}
