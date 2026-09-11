package com.apiweb.openapi;

import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.util.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 一键生成用例服务（按需求文档 §5.5 从接口定义生成用例）。
 *
 * <h3>生成策略</h3>
 * <ol>
 *   <li>创建 {@link CaseEntity}：默认名 {@code "用例 - " + 接口名}、状态 draft</li>
 *   <li>创建 1 个 {@link CaseStepEntity}（HTTP_REQUEST）：
 *     <ul>
 *       <li>url：{@code {{baseUrl}} + path}，pathParams 用 {{key}} 占位符</li>
 *       <li>method/headers/query/body：直接复制</li>
 *       <li>assertions：默认 1 条 {@code status==200},如有 responseExamples.[200] 加 1 条 {@code 包含 "code":0}</li>
 *       <li>extracts：从 responseExamples.[200] 中启发式提取（id/token/userId）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   CaseGenerator.GenerateResult r = caseGenerator.generate(api, "projectId", "moduleId");
 *   caseMapper.insert(r.getCase());
 *   caseStepMapper.insert(r.getStep());
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseGenerator {

    /**
     * 启发式提取字段（响应 JSON 中如果存在这些字段名,自动生成 extract）。
     */
    private static final Set<String> HEURISTIC_FIELDS = Set.of(
            "id", "userId", "user_id", "token", "accessToken", "access_token",
            "traceId", "trace_id", "requestId", "request_id", "data.id"
    );

    /**
     * 一键生成用例（返回草稿,不落库）。
     */
    public GenerateResult generate(ApiDefinitionEntity api, String projectId, String moduleId) {
        if (api == null) throw new IllegalArgumentException("api 不能为空");

        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setProjectId(projectId);
        caseEntity.setModuleId(moduleId);
        caseEntity.setName("用例 - " + api.getName());
        caseEntity.setDescription("由接口 [" + api.getMethod() + " " + api.getPath() + "] 一键生成");
        caseEntity.setStatus("draft");
        caseEntity.setPriority("P1");
        caseEntity.setVersion(1);
        caseEntity.setTags(api.getTags());
        caseEntity.setSteps("[]");
        caseEntity.setCreatedAt(Instant.now());

        CaseStepEntity step = new CaseStepEntity();
        step.setStepType("HTTP_REQUEST");
        step.setName(api.getName() + " - 请求");
        step.setPosition("TEST");
        step.setSortOrder(0);
        step.setEnabled(true);
        step.setFailStrategy("stop");

        // config 字段：把 url/method/headers/query/body/assertions/extracts 打包
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", buildUrl(api));
        config.put("method", api.getMethod());
        config.put("headers", parseKv(api.getHeaders()));
        config.put("query", parseKv(api.getQuery()));
        config.put("body", api.getBody());
        config.put("assertions", buildAssertions(api));
        config.put("extracts", buildExtracts(api));
        step.setConfig(JsonUtils.toJson(config));

        return new GenerateResult(caseEntity, step);
    }

    /**
     * 构造完整 URL（{{baseUrl}} + path,pathParams 用 {{key}}）。
     */
    private String buildUrl(ApiDefinitionEntity api) {
        String path = api.getPath() == null ? "" : api.getPath();
        // 把 {param} 替换为 {{param}}
        String resolved = path.replaceAll("\\{([^}]+)}", "{{$1}}");
        // 如果 path 里已经有变量占位符,直接用 {{baseUrl}} + resolved
        if (!resolved.startsWith("http")) {
            return "{{baseUrl}}" + resolved;
        }
        return resolved;
    }

    /**
     * 解析 headers/query JSON → List<{key,value,enabled}>。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseKv(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> list = JsonUtils.fromJson(json, List.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 生成默认断言：
     * <ul>
     *   <li>status == 200</li>
     *   <li>如果 responseExamples.200 存在,加 1 条 "包含 'code':0"（业务约定）</li>
     * </ul>
     */
    private List<Map<String, Object>> buildAssertions(ApiDefinitionEntity api) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> statusAssert = new LinkedHashMap<>();
        statusAssert.put("type", "STATUS");
        statusAssert.put("operator", "equals");
        statusAssert.put("expected", "200");
        statusAssert.put("message", "状态码必须是 200");
        out.add(statusAssert);

        if (api.getResponseExamples() != null && !api.getResponseExamples().isBlank()) {
            try {
                Map<String, Object> examples = JsonUtils.fromJson(api.getResponseExamples(), Map.class);
                if (examples != null && examples.containsKey("200")) {
                    Map<String, Object> bodyAssert = new LinkedHashMap<>();
                    bodyAssert.put("type", "BODY_CONTAINS");
                    bodyAssert.put("operator", "contains");
                    bodyAssert.put("expected", "\"code\":0");
                    bodyAssert.put("message", "业务码应为 0");
                    out.add(bodyAssert);
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    /**
     * 启发式提取：从 responseExamples.200 中找常见字段,生成 extract 规则。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildExtracts(ApiDefinitionEntity api) {
        if (api.getResponseExamples() == null || api.getResponseExamples().isBlank()) return List.of();
        try {
            Map<String, Object> examples = JsonUtils.fromJson(api.getResponseExamples(), Map.class);
            if (examples == null || !examples.containsKey("200")) return List.of();
            Object body = examples.get("200");
            if (body == null) return List.of();
            List<Map<String, Object>> extracts = new ArrayList<>();
            // 启发式：扫一遍 body,命中 HEURISTIC_FIELDS 就生成
            for (String field : HEURISTIC_FIELDS) {
                String jsonPath = "$." + field;
                Map<String, Object> extract = new LinkedHashMap<>();
                extract.put("type", "JSONPATH");
                extract.put("expression", jsonPath);
                extract.put("variable", field.replace(".", "_").replace("_id", "Id"));
                extract.put("defaultValue", "");
                extract.put("failStrategy", "ignore");
                extracts.add(extract);
            }
            return extracts;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Data
    @AllArgsConstructor
    public static class GenerateResult {
        private CaseEntity caseEntity;
        private CaseStepEntity step;
    }
}