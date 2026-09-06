package com.apiweb.service;

import com.apiweb.engine.CaseRunner;
import com.apiweb.entity.EnvironmentEntity;
import com.apiweb.entity.GlobalVariableEntity;
import com.apiweb.mapper.EnvironmentMapper;
import com.apiweb.mapper.GlobalVariableMapper;
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

    /** 供消费者复用的用例执行器（Spring 管理的 Bean） */
    public CaseRunner caseRunner() {
        return caseRunnerBean;
    }

    /**
     * 任务变量 JSON（[{key,value}]）转 Map。
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
     */
    public Map<String, String> buildEnvironmentVariables(String environmentId) {
        Map<String, String> map = new LinkedHashMap<>();
        if (environmentId == null) {
            return map;
        }
        EnvironmentEntity env = environmentMapper.selectById(environmentId);
        if (env == null) {
            return map;
        }
        if (env.getBaseUrl() != null) {
            map.put("baseUrl", env.getBaseUrl());
        }
        map.putAll(kvToMap(env.getVariables()));
        return map;
    }

    /**
     * 组装项目全局变量。
     */
    public Map<String, String> buildGlobalVariables(String projectId) {
        Map<String, String> map = new LinkedHashMap<>();
        List<GlobalVariableEntity> globals = globalVariableMapper.select(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId));
        for (GlobalVariableEntity g : globals) {
            map.put(g.getName(), g.getValue() == null ? "" : g.getValue());
        }
        return map;
    }

    /**
     * 报告明细超阈值时转存 MinIO，返回 details 内容或 minio:// 引用。
     */
    public String offloadDetails(String runId, String detailsJson) {
        return ossService.putIfLarge(detailsJson, "reports/" + runId + "/details.json");
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
