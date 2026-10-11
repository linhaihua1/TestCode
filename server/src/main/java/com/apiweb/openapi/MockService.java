package com.apiweb.openapi;

import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.openapi.mock.MockEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.List;
import java.util.Map;

/**
 * Mock 接口匹配与执行服务（需求文档 §5.4）。
 *
 * <p>职责：根据项目 ID + HTTP 方法 + 请求路径，在已开启 mock 的接口定义中
 * 匹配出唯一接口（支持 Ant 风格路径），并驱动 {@link MockEngine} 产出响应。
 * 数据访问、匹配与执行逻辑在此收敛，供 {@link MockController}（HTTP 路由层）调用，
 * 避免表现层直接操作 Mapper 或引擎。
 */
@Service
@RequiredArgsConstructor
public class MockService {

    private final ApiDefinitionMapper apiMapper;
    private final MockEngine mockEngine;

    /** Ant 风格路径匹配器（复用单例，线程安全）。 */
    private static final PathMatcher MATCHER = new AntPathMatcher();

    /**
     * 匹配 mock 接口。
     *
     * @param projectId 项目 ID（mock 路径第一段）
     * @param method    HTTP 方法（大写）
     * @param path      请求路径（以 / 开头）
     * @return 匹配到的接口定义；找不到返回 {@code null}
     */
    public ApiDefinitionEntity match(String projectId, String method, String path) {
        // 查询该项目下、同方法、已开启 mock 的所有接口
        List<ApiDefinitionEntity> candidates = apiMapper.selectList(
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .eq(ApiDefinitionEntity::getMethod, method)
                        .eq(ApiDefinitionEntity::getMockEnabled, true));
        if (candidates.isEmpty()) {
            return null;
        }
        // Ant 风格路径匹配，命中第一个即返回
        return candidates.stream()
                .filter(c -> matchPath(path, c.getPath()))
                .findFirst().orElse(null);
    }

    /**
     * 执行 mock 逻辑，产出响应。
     *
     * @param api      匹配到的接口定义
     * @param method   HTTP 方法（大写）
     * @param path     请求路径
     * @param query    解析后的 query 参数（key → values）
     * @param body     原始请求体（可能为 null）
     * @return mock 响应（状态码 + 响应头 + 响应体）
     */
    public MockEngine.MockResponse execute(ApiDefinitionEntity api, String method, String path,
                                           Map<String, List<String>> query, String body) {
        return mockEngine.execute(api, method, path, query, body);
    }

    /**
     * 路径匹配（Ant 风格，支持 {@code /users/{id}} 匹配 {@code /users/123}）。
     */
    private boolean matchPath(String actual, String pattern) {
        if (actual == null || pattern == null) return false;
        return MATCHER.match(pattern, actual);
    }
}
