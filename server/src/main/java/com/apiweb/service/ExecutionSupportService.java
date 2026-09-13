package com.apiweb.service;

import com.apiweb.engine.CaseRunner;
import com.apiweb.engine.VariablesResolver;
import com.apiweb.engine.variable.VariableMerger;
import com.apiweb.entity.EnvironmentEntity;
import com.apiweb.entity.GlobalVariableEntity;
import com.apiweb.mapper.EnvironmentMapper;
import com.apiweb.mapper.GlobalVariableMapper;
import com.apiweb.security.AesGcm;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行支撑服务：变量组装、大报告转存 MinIO、任务回调。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionSupportService {

    private final EnvironmentMapper environmentMapper;
    private final GlobalVariableMapper globalVariableMapper;
    private final OssService ossService;
    private final CaseRunner caseRunnerBean;
    private final VariableMerger variableMerger;

    /**
     * 供 MQ 消费者复用的用例执行器（Spring 管理的 Bean）。
     *
     * <p>执行器本身有状态依赖（HTTP 客户端等），由 Spring 单例托管并复用，
     * 避免在每次消费消息时重复创建。</p>
     *
     * @return 用例执行器实例
     */
    public CaseRunner caseRunner() {
        return caseRunnerBean;
    }

    /**
     * 将任务变量 JSON（形如 {@code [{key,value}]}）转为 Map，便于后续变量替换。
     *
     * @param kvJson 变量 JSON 数组字符串（为空的字段按空列表处理）
     * @return key -> value 映射（value 为 null 时归一为 ""）
     */
    public Map<String, String> kvToMap(String kvJson) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> kv : JsonUtils.toList(kvJson)) {
            Object key = kv.get("key");
            if (key != null) {
                map.put(String.valueOf(key),
                        kv.get("value") == null ? "" : String.valueOf(kv.get("value")));
            }
        }
        return map;
    }

    /**
     * 组装环境变量（含 baseUrl）。
     *
     * <p>副作用：读取数据库环境配置；{@code environmentId} 为空或环境不存在时返回空 Map，
     * 不抛异常（环境为可选项）。</p>
     *
     * @param environmentId 环境 ID（可为 null）
     * @return 变量名 -> 变量值 映射，固定含 {@code baseUrl}（若存在）
     */
    public Map<String, String> buildEnvironmentVariables(String environmentId) {
        Map<String, String> map = new LinkedHashMap<>();
        if (environmentId == null) return map;
        EnvironmentEntity env = environmentMapper.selectById(environmentId);
        if (env == null) return map;
        if (env.getBaseUrl() != null) map.put("baseUrl", env.getBaseUrl());
        map.putAll(kvToMap(env.getVariables()));
        return map;
    }

    /**
     * 组装项目全局变量（SECRET 类型自动 AES 解密）。
     *
     * <p>副作用：读取 {@code t_global_variable}。对 {@code type=SECRET} 的变量调用
     * {@link AesGcm#decrypt} 还原明文；解密失败时记警告并置空，保证其它变量不受影响。</p>
     *
     * @param projectId 项目 ID
     * @return 全局变量名 -> 值 映射
     */
    public Map<String, String> buildGlobalVariables(String projectId) {
        Map<String, String> map = new LinkedHashMap<>();
        List<GlobalVariableEntity> globals = globalVariableMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId));
        for (GlobalVariableEntity g : globals) {
            String value = g.getValue() == null ? "" : g.getValue();
            if ("SECRET".equalsIgnoreCase(g.getType())) {
                try {
                    value = AesGcm.decrypt(value);
                } catch (Exception e) {
                    log.warn("全局变量 [{}] 解密失败: {}", g.getName(), e.getMessage());
                    value = "";
                }
            }
            map.put(g.getName(), value);
        }
        return map;
    }

    /**
     * 按 §2 优先级合并多来源变量（提取 > 用例 > 环境 > 全局）。
     *
     * <p>调试执行时使用,生成的 VariablesResolver 自动处理 {{var}} 占位符 + SECRET 解密。
     */
    public VariablesResolver buildMergedResolver(String projectId, String environmentId,
                                                  List<VariableMerger.CaseVariable> caseVars,
                                                  Map<String, String> extracts) {
        List<GlobalVariableEntity> globals = globalVariableMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId));
        EnvironmentEntity env = environmentId == null ? null : environmentMapper.selectById(environmentId);
        return variableMerger.buildResolver(globals, env, caseVars, extracts);
    }

    /**
     * 报告明细超阈值时转存 MinIO，返回 details 内容或 minio:// 引用。
     */
    public String offloadDetails(String runId, String detailsJson) {
        return ossService.putIfLarge(detailsJson, OssKeys.reportDetails(runId));
    }

    /**
     * 任务完成回调通知（尽力而为，失败不影响主流程）。
     */
    public void notifyCallback(String url, String payload) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("任务回调通知失败: {} - {}", url, e.getMessage());
        }
    }
}
