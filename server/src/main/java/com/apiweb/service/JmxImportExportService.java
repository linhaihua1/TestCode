package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.service.PerfExecutionService.JmxBuilder;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.protocol.http.util.HTTPArgument;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JMX 导入 / 导出服务：在性能用例与标准 JMeter .jmx 文件之间双向转换。
 *
 * <p>导出：复用 {@link JmxBuilder} 生成标准 .jmx 文本（含线程组 / HTTP 请求 / 请求头 / 变量）。</p>
 * <p>导入：用 {@link SaveService#loadTree} 解析 .jmx 为 HashTree，遍历提取
 * {@link ThreadGroup}（线程数 / ramp-up / 循环 / 持续时间）与 {@link HTTPSamplerProxy}
 * （协议 / 域名 / 端口 / 路径 / 方法 / 参数 / 请求体 / 请求头），映射回 {@link PerfCaseEntity}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JmxImportExportService {

    /** 导出用例为 .jmx 文本。 */
    public String exportJmx(PerfCaseEntity c) {
        return JmxBuilder.build(c);
    }

    /** 批量导出多个用例，合并为一个 .jmx（一个 TestPlan + 多个 ThreadGroup）。 */
    public String exportJmxMulti(List<PerfCaseEntity> cases) {
        return JmxBuilder.buildMulti(cases);
    }

    /**
     * 解析 .jmx 文本为性能用例（不含 projectId/name 的持久化，仅填充结构字段）。
     * 调用方负责设置 projectId / name。
     */
    public PerfCaseEntity importJmx(String jmxXml) {
        try {
            File tmp = File.createTempFile("apiweb-import-", ".jmx");
            Files.writeString(tmp.toPath(), jmxXml);
            HashTree tree = SaveService.loadTree(tmp);
            Files.deleteIfExists(tmp.toPath());

            PerfCaseEntity c = new PerfCaseEntity();
            c.setThreads(10);
            c.setRampUp(5);
            c.setLoops(1);
            c.setDuration(0);
            c.setThinkTime(0);
            c.setOnSampleError("continue");
            c.setVariables("[]");
            c.setProfile("{\"loadProfile\":\"fixed\"}");

            List<Map<String, Object>> steps = new ArrayList<>();
            traverse(tree, c, steps);

            if (steps.isEmpty()) {
                throw BizException.badRequest("未在 .jmx 中找到 HTTP 请求采样器");
            }
            c.setSteps(JsonUtils.toJson(steps));
            return c;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("JMX 导入失败", e);
            throw BizException.badRequest("JMX 文件解析失败：" + e.getMessage());
        }
    }

    /**
     * 递归遍历 HashTree，提取 ThreadGroup 配置与 HTTP 采样器。
     */
    private void traverse(HashTree tree, PerfCaseEntity c, List<Map<String, Object>> steps) {
        for (Object key : tree.list()) {
            TestElement el = (TestElement) key;
            if (el instanceof ThreadGroup tg) {
                extractThreadGroup(tg, c);
            } else if (el instanceof HTTPSamplerProxy sampler) {
                steps.add(extractSampler(sampler, tree.get(key)));
            }
            HashTree sub = tree.get(key);
            if (sub != null) {
                traverse(sub, c, steps);
            }
        }
    }

    /** 提取线程组：线程数 / ramp-up / 循环 / 持续时间 / 失败策略。 */
    private void extractThreadGroup(ThreadGroup tg, PerfCaseEntity c) {
        try {
            c.setThreads(tg.getNumThreads());
        } catch (Exception ignored) {
        }
        try {
            c.setRampUp((int) tg.getRampUp());
        } catch (Exception ignored) {
        }
        try {
            int loops = tg.getSamplerController() == null ? 1
                    : tg.getSamplerController().getPropertyAsInt("LoopController.loops", 1);
            c.setLoops(loops < 0 ? 1 : loops);
        } catch (Exception ignored) {
        }
        try {
            c.setDuration((int) tg.getDuration());
        } catch (Exception ignored) {
        }
        try {
            String onError = tg.getPropertyAsString("ThreadGroup.on_sample_error");
            c.setOnSampleError(onError == null || onError.isBlank() ? "continue" : onError);
        } catch (Exception ignored) {
        }
    }

    /** 提取 HTTP 采样器：协议/域名/端口/路径/方法/参数/请求体，及子节点中的请求头。 */
    private Map<String, Object> extractSampler(HTTPSamplerProxy sampler, HashTree subTree) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", sampler.getName());
        step.put("protocol", sampler.getPropertyAsString(HTTPSamplerBase.PROTOCOL, "https"));
        step.put("method", sampler.getMethod());
        step.put("host", sampler.getPropertyAsString(HTTPSamplerBase.DOMAIN, ""));
        step.put("port", sampler.getPropertyAsString(HTTPSamplerBase.PORT, ""));
        step.put("path", sampler.getPropertyAsString(HTTPSamplerBase.PATH, ""));
        step.put("encoding", sampler.getPropertyAsString(HTTPSamplerBase.CONTENT_ENCODING, "UTF-8"));

        // 查询参数 + 请求体
        List<Map<String, Object>> queryParams = new ArrayList<>();
        StringBuilder bodyBuilder = new StringBuilder();
        boolean useRawBody = sampler.getPropertyAsBoolean("HTTPSampler.postBodyRaw", false);
        Arguments args = sampler.getArguments();
        if (args != null) {
            for (int i = 0; i < args.getArgumentCount(); i++) {
                var arg = args.getArgument(i);
                String name = arg.getName();
                String value = arg.getValue();
                if (useRawBody) {
                    // 原始请求体：合并所有参数的 value
                    if (i > 0) {
                        bodyBuilder.append('&');
                    }
                    bodyBuilder.append(name).append('=').append(value);
                } else {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("key", name);
                    q.put("value", value);
                    queryParams.add(q);
                }
            }
        }
        step.put("queryParams", queryParams);
        step.put("body", bodyBuilder.toString());

        // 子节点中的请求头（HeaderManager）
        List<Map<String, Object>> headers = new ArrayList<>();
        if (subTree != null) {
            for (Object child : subTree.list()) {
                if (child instanceof HeaderManager hm) {
                    for (int i = 0; i < hm.size(); i++) {
                        try {
                            Header h = hm.getHeader(i);
                            if (h != null) {
                                Map<String, Object> hv = new LinkedHashMap<>();
                                hv.put("key", h.getName());
                                hv.put("value", h.getValue());
                                headers.add(hv);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        step.put("headers", headers);
        return step;
    }
}
