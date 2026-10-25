package com.apiweb.openapi;

import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.openapi.mock.MockEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.time.Instant;
import java.util.Comparator;
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

    /** 「最新优先」比较器：按创建时间倒序，null 视为最旧，保证排序结果确定。 */
    private static final Comparator<ApiDefinitionEntity> NEWEST_FIRST =
            Comparator.comparing((ApiDefinitionEntity api) ->
                            api.getCreatedAt() == null ? Instant.EPOCH : api.getCreatedAt())
                    .reversed();

    /**
     * 匹配 mock 接口。
     *
     * <h3>为什么需要"确定性择优"</h3>
     * <p>历史数据里可能存在同 {@code method + path} 的多条接口（早期允许重复创建）。
     * 若直接 {@code findFirst()}，命中哪一条取决于数据库返回顺序，表现为 Mock
     * 响应"时而生效时而返回旧内容"，且从界面完全看不出来。因此这里固定两条规则：</p>
     * <ol>
     *   <li><b>精确路径优先</b>：{@code /users/login} 不应被模板 {@code /users/{id}} 抢走；</li>
     *   <li><b>同优先级取最新</b>：按创建时间倒序，新配置覆盖旧配置（符合"后改先生效"直觉）。</li>
     * </ol>
     *
     * <p>另外只匹配未软删的接口——否则回收站里的接口依然对外提供 Mock 服务。</p>
     *
     * @param projectId 项目 ID（mock 路径第一段）
     * @param method    HTTP 方法（大写）
     * @param path      请求路径（以 / 开头）
     * @return 匹配到的接口定义；找不到返回 {@code null}
     */
    public ApiDefinitionEntity match(String projectId, String method, String path) {
        // 查询该项目下、同方法、已开启 mock 且未删除的接口
        List<ApiDefinitionEntity> candidates = apiMapper.selectList(
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .eq(ApiDefinitionEntity::getMethod, method)
                        .eq(ApiDefinitionEntity::getMockEnabled, true)
                        .isNull(ApiDefinitionEntity::getDeletedAt));
        if (candidates.isEmpty()) {
            return null;
        }
        // 统一先按「最新优先」排序，两条规则都在这份有序列表上做，结果才可复现
        List<ApiDefinitionEntity> ordered = candidates.stream().sorted(NEWEST_FIRST).toList();
        // 规则 1：精确路径优先
        for (ApiDefinitionEntity candidate : ordered) {
            if (path != null && path.equals(candidate.getPath())) {
                return candidate;
            }
        }
        // 规则 2：Ant 模板匹配（ordered 已是最新在前，命中即最新一条）
        for (ApiDefinitionEntity candidate : ordered) {
            if (matchPath(path, candidate.getPath())) {
                return candidate;
            }
        }
        return null;
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
