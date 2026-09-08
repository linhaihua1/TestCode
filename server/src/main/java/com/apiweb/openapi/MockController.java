package com.apiweb.openapi;

import com.apiweb.engine.HttpExecutor;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.openapi.mock.MockEngine;
import com.apiweb.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock 控制器（按需求文档 §5.4 提供 4 档 Mock 服务）。
 *
 * <h3>路径约定</h3>
 * <ul>
 *   <li>所有 Mock 请求走 {@code /api/mock/**} 前缀,不走 /api/v1（区别于正常 API）</li>
 *   <li>第一个 path 段是 projectId（便于快速按项目过滤）</li>
 *   <li>剩余路径作为 mock key,与 ApiDefinition.path 匹配</li>
 * </ul>
 *
 * <p>例如 {@code POST /api/mock/{projectId}/users/login} 会去 t_api_definition 中查
 * {@code path=/users/login, method=POST, project_id=projectId, mock_enabled=1} 的接口,
 * 然后按 mockType 执行对应策略。
 *
 * <p>路径匹配支持 Ant 风格：{@code /users/{id}} 能匹配 {@code /users/123}。
 */
@Slf4j
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
public class MockController {

    private final ApiDefinitionMapper apiMapper;
    private final MockEngine mockEngine;

    private static final PathMatcher MATCHER = new AntPathMatcher();

    /**
     * 兜底路由：匹配所有方法、所有 path。
     *
     * <p>Spring MVC 会先匹配更具体的路径,这里作为通用兜底。
     */
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD,
            RequestMethod.OPTIONS})
    public ResponseEntity<String> dispatch(HttpServletRequest request,
                                           @RequestBody(required = false) String body) {
        String fullPath = request.getRequestURI();
        String mockPrefix = "/api/mock/";
        if (!fullPath.startsWith(mockPrefix)) {
            return ResponseEntity.status(404).body("{\"error\":\"invalid mock path\"}");
        }
        String sub = fullPath.substring(mockPrefix.length());
        int slash = sub.indexOf('/');
        if (slash <= 0) {
            return ResponseEntity.status(400).body("{\"error\":\"missing projectId or path\"}");
        }
        String projectId = sub.substring(0, slash);
        String mockPath = "/" + sub.substring(slash + 1);
        String method = request.getMethod().toUpperCase();

        // 查询该项目的所有 mock 接口
        List<ApiDefinitionEntity> candidates = apiMapper.selectList(
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .eq(ApiDefinitionEntity::getMethod, method)
                        .eq(ApiDefinitionEntity::getMockEnabled, true));
        if (candidates.isEmpty()) {
            return ResponseEntity.status(404).body("{\"error\":\"no mock api found\"}");
        }
        // 路径匹配：Ant 风格
        ApiDefinitionEntity matched = candidates.stream()
                .filter(c -> matchPath(mockPath, c.getPath()))
                .findFirst().orElse(null);
        if (matched == null) {
            return ResponseEntity.status(404).body("{\"error\":\"no mock matched: " + method + " " + mockPath + "\"}");
        }

        Map<String, List<String>> query = parseQuery(request.getQueryString());
        MockEngine.MockResponse resp = mockEngine.execute(matched, method, mockPath, query, body);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(resp.getStatus());
        if (resp.getHeaders() != null) {
            resp.getHeaders().forEach(builder::header);
        }
        return builder.body(resp.getBody() == null ? "{}" : resp.getBody());
    }

    /**
     * 路径匹配（Ant 风格）。
     */
    private boolean matchPath(String actual, String pattern) {
        if (actual == null || pattern == null) return false;
        return MATCHER.match(pattern, actual);
    }

    /**
     * 解析 query string。
     */
    private Map<String, List<String>> parseQuery(String qs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (qs == null || qs.isBlank()) return map;
        for (String segment : qs.split("&")) {
            int eq = segment.indexOf('=');
            String key = eq < 0 ? segment : segment.substring(0, eq);
            String value = eq < 0 ? "" : segment.substring(eq + 1);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }
}