package com.apiweb.service;

import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.JmeterBootstrapper;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.mapper.PerfCaseMapper;
import com.apiweb.mapper.PerfReportMapper;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.samplers.SampleSaveConfiguration;
import org.apache.jorphan.collections.HashTree;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 性能测试执行服务（嵌入式 JMeter）。
 *
 * <p>执行流程：
 * <ol>
 *   <li>{@link JmxBuilder} 把 PerfCase 配置生成 .jmx（in-memory）</li>
 *   <li>{@link SaveService#loadTree} 解析成 HashTree</li>
 *   <li>挂上 {@link ResultCollector} 监听器，把结果落到 .jtl（CSV）</li>
 *   <li>{@link StandardJMeterEngine#runTest} 在当前 JVM 内执行（自带线程池）</li>
 *   <li>{@link JtlParser} 把 .jtl 解析成指标</li>
 * </ol>
 *
 * <p>整个流程不依赖任何外部进程：JMeter 引擎以 jar 依赖的形式随项目启动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfExecutionService {

    private final PerfCaseMapper perfCaseMapper;
    private final PerfReportMapper perfReportMapper;
    private final OssService ossService;
    private final JmeterBootstrapper jmeterBootstrapper;

    @Async
    public void executeAsync(EngineDtos.TaskMessage message) {
        execute(message.getRunId(), message.getCaseId());
    }

    public void execute(String runId, String caseId) {
        PerfCaseEntity perfCase = perfCaseMapper.selectById(caseId);
        PerfReportEntity report = perfReportMapper.selectById(runId);
        if (perfCase == null || report == null) {
            log.warn("性能用例或报告不存在: caseId={}, runId={}", caseId, runId);
            return;
        }
        long start = System.currentTimeMillis();
        File jtlFile = null;
        StandardJMeterEngine engine = null;
        try {
            if (!jmeterBootstrapper.isInitialized()) {
                throw new IllegalStateException("嵌入式 JMeter 未初始化完成");
            }
            File workDir = Files.createTempDirectory("apiweb-perf-").toFile();
            jtlFile = new File(workDir, runId + ".jtl");

            // 1. 生成 .jmx 并加载为测试计划树
            String jmx = JmxBuilder.build(perfCase);
            File jmxFile = new File(workDir, runId + ".jmx");
            Files.writeString(jmxFile.toPath(), jmx);
            HashTree testPlanTree = SaveService.loadTree(jmxFile);

            // 2. 创建结果收集器（直接写 .jtl CSV 文件，保留与旧版本兼容的字段顺序）
            ResultCollector collector = createResultCollector(jtlFile);
            Object[] root = testPlanTree.getArray();
            if (root.length == 0) {
                throw new IllegalStateException("生成的 .jmx 缺少根节点（TestPlan）");
            }
            testPlanTree.add(root[0], collector);

            // 3. 同步执行（StandardJMeterEngine 是单次执行的，非线程安全）
            engine = new StandardJMeterEngine();
            engine.configure(testPlanTree);
            engine.runTest();

            // 4. 等待执行结束（最大 2 小时）
            long deadline = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
            while (engine.isActive()) {
                if (System.currentTimeMillis() > deadline) {
                    engine.stopTest(true);
                    throw new IllegalStateException("JMeter 执行超时（2 小时）");
                }
                Thread.sleep(500);
            }

            // 5. 解析 .jtl
            if (!jtlFile.exists() || jtlFile.length() == 0) {
                throw new IllegalStateException("JMeter 未生成结果文件，请检查用例配置（线程数/持续时间）");
            }
            JtlSummary summary = JtlParser.parse(jtlFile);
            report.setStatus(summary.errorCount == 0 && summary.sampleCount > 0
                    ? "success" : "failed");
            report.setDuration((int) (System.currentTimeMillis() - start));
            report.setSummary(JsonUtils.toJson(Map.of(
                    "sampleCount", summary.sampleCount,
                    "errorCount", summary.errorCount,
                    "avg", summary.avg,
                    "min", summary.min,
                    "max", summary.max,
                    "p90", summary.p90,
                    "p95", summary.p95,
                    "p99", summary.p99,
                    "tps", summary.tps)));
            report.setSeries(JsonUtils.toJson(summary.series));
            report.setLabels(JsonUtils.toJson(summary.labels));
            report.setErrors(JsonUtils.toJson(summary.errors));
        } catch (Exception e) {
            log.error("性能测试执行异常", e);
            report.setStatus("error");
            report.setMessage(e.getMessage());
            report.setDuration((int) (System.currentTimeMillis() - start));
        } finally {
            if (engine != null) {
                try {
                    engine.stopTest(true);
                } catch (Exception ignored) {
                }
            }
        }
        perfReportMapper.updateById(report);
    }

    /**
     * 构造一个 CSV 格式的 ResultCollector，输出与旧 JtlParser 兼容的字段顺序。
     */
    private ResultCollector createResultCollector(File jtlFile) {
        ResultCollector collector = new ResultCollector();
        SampleSaveConfiguration saveConfig = new SampleSaveConfiguration();
        // CSV 格式输出（JMeter 5.6 没有 setFormatter，通过 JMeterProperties 控制）
        saveConfig.setAsXml(false);
        saveConfig.setTime(true);
        saveConfig.setLabel(true);
        saveConfig.setCode(true);                 // responseCode
        saveConfig.setMessage(true);
        saveConfig.setThreadName(true);
        saveConfig.setDataType(true);
        saveConfig.setSuccess(true);
        saveConfig.setAssertionResultsFailureMessage(true);  // JMeter 5.6 重命名自 setFailureMessage
        saveConfig.setBytes(true);
        saveConfig.setSentBytes(true);
        saveConfig.setThreadCounts(true);        // JMeter 5.6 重命名自 setGrpThreads/setAllThreads
        saveConfig.setUrl(true);                  // JMeter 5.6 重命名自 setURL
        saveConfig.setLatency(true);
        saveConfig.setConnectTime(true);
        saveConfig.setEncoding(false);
        saveConfig.setIdleTime(false);
        saveConfig.setTimestamp(true);            // JMeter 5.6 重命名自 setTimestampFormat
        saveConfig.setFieldNames(true);           // JMeter 5.6 重命名自 setPrintFieldNames
        collector.setSaveConfig(saveConfig);
        collector.setFilename(jtlFile.getAbsolutePath());
        collector.setErrorLogging(false);
        return collector;
    }

    /** .jtl 汇总统计 */
    public record JtlSummary(int sampleCount, int errorCount, double avg, long min, long max,
                             long p90, long p95, long p99, double tps,
                             List<Map<String, Object>> series,
                             List<Map<String, Object>> labels,
                             List<Map<String, Object>> errors) {}

    /**
     * JTL CSV 解析器（输出格式：timeStamp,elapsed,label,responseCode,responseMessage,
     * threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,
     * URL,Latency,IdleTime,Connect）。
     */
    public static final class JtlParser {
        private JtlParser() {}

        public static JtlSummary parse(File jtlFile) throws Exception {
            List<String> lines = Files.readAllLines(jtlFile.toPath());
            List<Long> elapsedList = new ArrayList<>();
            List<Long> timeStamps = new ArrayList<>();
            Map<String, List<Long>> byLabel = new LinkedHashMap<>();
            Map<String, Integer> errorByMsg = new LinkedHashMap<>();
            int errors = 0;
            // 跳过表头（若含 print_field_names=true，第一行是字段名）
            int start = 0;
            if (!lines.isEmpty() && lines.get(0).startsWith("timeStamp,")) {
                start = 1;
            }
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (cols.length < 4) {
                    continue;
                }
                long ts = Long.parseLong(cols[0].trim());
                long elapsed = Long.parseLong(cols[1].trim());
                String label = cols[2];
                String responseCode = cols[3];
                boolean success = cols.length > 7 && "true".equalsIgnoreCase(cols[7].trim());
                timeStamps.add(ts);
                elapsedList.add(elapsed);
                byLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(elapsed);
                if (!success || responseCode.startsWith("4") || responseCode.startsWith("5")) {
                    errors++;
                    String msg = cols.length > 8 && !cols[8].isBlank() ? cols[8] : "HTTP " + responseCode;
                    errorByMsg.merge(msg, 1, Integer::sum);
                }
            }
            int n = elapsedList.size();
            if (n == 0) {
                return new JtlSummary(0, 0, 0, 0, 0, 0, 0, 0, 0,
                        List.of(), List.of(), List.of());
            }
            List<Long> sorted = new ArrayList<>(elapsedList);
            sorted.sort(Long::compareTo);
            long minTs = timeStamps.stream().min(Long::compareTo).orElse(0L);
            long maxTs = timeStamps.stream().max(Long::compareTo).orElse(0L);
            double durationSec = Math.max(0.001, (maxTs - minTs + sorted.get(n - 1)) / 1000.0);
            double avg = elapsedList.stream().mapToLong(Long::longValue).average().orElse(0);

            List<Map<String, Object>> series = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("ts", (timeStamps.get(i) - minTs) / 1000.0);
                point.put("elapsed", elapsedList.get(i));
                series.add(point);
            }
            List<Map<String, Object>> labelStats = new ArrayList<>();
            byLabel.forEach((label, list) -> {
                List<Long> s = new ArrayList<>(list);
                s.sort(Long::compareTo);
                Map<String, Object> stat = new LinkedHashMap<>();
                stat.put("label", label);
                stat.put("count", s.size());
                stat.put("avg", s.stream().mapToLong(Long::longValue).average().orElse(0));
                stat.put("p90", percentile(s, 0.90));
                stat.put("p95", percentile(s, 0.95));
                stat.put("p99", percentile(s, 0.99));
                labelStats.add(stat);
            });
            List<Map<String, Object>> errorList = new ArrayList<>();
            errorByMsg.forEach((msg, count) -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("message", msg);
                e.put("count", count);
                errorList.add(e);
            });
            return new JtlSummary(n, errors, avg,
                    sorted.get(0), sorted.get(n - 1),
                    percentile(sorted, 0.90), percentile(sorted, 0.95),
                    percentile(sorted, 0.99), n / durationSec,
                    series, labelStats, errorList);
        }

        private static long percentile(List<Long> sorted, double p) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int idx = (int) Math.ceil(p * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
        }
    }

    /**
     * JMX 生成器：把 PerfCase 结构转换为标准 .jmx（支持阶梯加压/目标并发线程组）。
     */
    public static final class JmxBuilder {
        private JmxBuilder() {}

        public static String build(PerfCaseEntity c) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6\">\n");
            sb.append("  <hashTree>\n");
            appendTestPlan(sb, c.getName());
            sb.append("    <hashTree>\n");
            appendThreadGroup(sb, c, "    ");
            sb.append("    </hashTree>\n");
            sb.append("  </hashTree>\n</jmeterTestPlan>\n");
            return sb.toString();
        }

        /**
         * 批量导出：多个用例合并为一个 .jmx（一个 TestPlan + 多个 ThreadGroup）。
         * 每个用例作为一个独立的线程组，testname 用用例名区分。
         */
        public static String buildMulti(List<PerfCaseEntity> cases) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6\">\n");
            sb.append("  <hashTree>\n");
            appendTestPlan(sb, "批量导出测试计划");
            sb.append("    <hashTree>\n");
            for (PerfCaseEntity c : cases) {
                appendThreadGroup(sb, c, "    ");
            }
            sb.append("    </hashTree>\n");
            sb.append("  </hashTree>\n</jmeterTestPlan>\n");
            return sb.toString();
        }

        /** 生成 TestPlan 元素（含用户变量占位）。 */
        private static void appendTestPlan(StringBuilder sb, String testName) {
            sb.append("    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" ");
            sb.append("testname=\"").append(escape(testName)).append("\">\n");
            sb.append("      <boolProp name=\"TestPlan.functional_mode\">false</boolProp>\n");
            sb.append("      <boolProp name=\"TestPlan.serialize_threadgroups\">false</boolProp>\n");
            sb.append("      <elementProp name=\"TestPlan.user_defined_variables\" elementType=\"Arguments\">\n");
            sb.append("        <collectionProp name=\"Arguments.arguments\"/>\n");
            sb.append("      </elementProp>\n");
            sb.append("    </TestPlan>\n");
        }

        /** 生成一个 ThreadGroup（线程组 + 其下的用户变量 / HTTP 请求 / 思考时间）。 */
        private static void appendThreadGroup(StringBuilder sb, PerfCaseEntity c, String indent) {
            Map<String, Object> profile = JsonUtils.toMap(c.getProfile());
            String loadProfile = String.valueOf(profile.getOrDefault("loadProfile", "fixed"));
            int threads = c.getThreads();
            int rampUp = c.getRampUp();
            // 阶梯加压：threads 为峰值，按 5 级阶梯到达
            int steps = "stepping".equals(loadProfile) ? 5 : 1;
            int loops = c.getLoops() != null && c.getLoops() > 0 ? c.getLoops() : 1;
            int duration = c.getDuration() != null ? c.getDuration() : 0;
            boolean scheduled = duration > 0;

            sb.append(indent).append("<ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" ");
            sb.append("testname=\"").append(escape(c.getName())).append("\">\n");
            sb.append(indent).append("  <stringProp name=\"ThreadGroup.num_threads\">")
                    .append(threads).append("</stringProp>\n");
            sb.append(indent).append("  <stringProp name=\"ThreadGroup.ramp_time\">")
                    .append(Math.max(rampUp, steps)).append("</stringProp>\n");
            sb.append(indent).append("  <boolProp name=\"ThreadGroup.scheduler\">")
                    .append(scheduled).append("</boolProp>\n");
            sb.append(indent).append("  <stringProp name=\"ThreadGroup.on_sample_error\">")
                    .append(c.getOnSampleError() == null ? "continue" : c.getOnSampleError())
                    .append("</stringProp>\n");
            if (scheduled) {
                sb.append(indent).append("  <stringProp name=\"ThreadGroup.duration\">")
                        .append(duration).append("</stringProp>\n");
            }
            // main_controller（LoopController）：ThreadGroup 必需，缺失会导致
            // "Property ThreadGroup.main_controller is unset" 异常
            sb.append(indent).append("  <elementProp name=\"ThreadGroup.main_controller\" ")
                    .append("elementType=\"LoopController\" guiclass=\"LoopControlPanel\" ")
                    .append("testclass=\"LoopController\" testname=\"循环控制器\">\n");
            sb.append(indent).append("    <boolProp name=\"LoopController.continue_forever\">false</boolProp>\n");
            sb.append(indent).append("    <stringProp name=\"LoopController.loops\">")
                    .append(scheduled ? -1 : loops).append("</stringProp>\n");
            sb.append(indent).append("  </elementProp>\n");
            sb.append(indent).append("</ThreadGroup>\n");
            sb.append(indent).append("<hashTree>\n");

            // 用户自定义变量
            List<Map<String, Object>> vars = JsonUtils.toList(c.getVariables());
            if (!vars.isEmpty()) {
                sb.append("      <Arguments guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" ");
                sb.append("testname=\"用户定义的变量\">\n        <collectionProp name=\"Arguments.arguments\">\n");
                for (Map<String, Object> v : vars) {
                    sb.append("          <elementProp name=\"").append(escape(str(v.get("key"))))
                            .append("\" elementType=\"Argument\">\n");
                    sb.append("            <stringProp name=\"Argument.name\">")
                            .append(escape(str(v.get("key")))).append("</stringProp>\n");
                    sb.append("            <stringProp name=\"Argument.value\">")
                            .append(escape(str(v.get("value")))).append("</stringProp>\n");
                    sb.append("          </elementProp>\n");
                }
                sb.append("        </collectionProp>\n      </Arguments>\n      <hashTree/>\n");
            }

            // HTTP 请求步骤
            for (Map<String, Object> step : JsonUtils.toList(c.getSteps())) {
                String stepName = str(step.getOrDefault("name", "HTTP请求"));
                String protocol = str(step.getOrDefault("protocol", "https"));
                String method = str(step.getOrDefault("method", "GET"));
                String host = str(step.getOrDefault("host", ""));
                String port = str(step.getOrDefault("port", ""));
                String path = str(step.getOrDefault("path", ""));
                String encoding = str(step.getOrDefault("encoding", "UTF-8"));
                sb.append("      <HTTPSamplerProxy guiclass=\"HttpTestSampleGui\" ");
                sb.append("testclass=\"HTTPSamplerProxy\" testname=\"").append(escape(stepName)).append("\">\n");
                sb.append("        <stringProp name=\"HTTPSampler.domain\">")
                        .append(escape(host)).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.port\">")
                        .append(escape(port)).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.protocol\">")
                        .append(escape(protocol)).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.contentEncoding\">")
                        .append(escape(encoding)).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.path\">")
                        .append(escape(path)).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.method\">")
                        .append(escape(method)).append("</stringProp>\n");
                sb.append("        <boolProp name=\"HTTPSampler.follow_redirects\">")
                        .append(Boolean.parseBoolean(str(step.getOrDefault("followRedirects", "true"))))
                        .append("</boolProp>\n");
                sb.append("        <boolProp name=\"HTTPSampler.use_keepalive\">")
                        .append(Boolean.parseBoolean(str(step.getOrDefault("useKeepAlive", "true"))))
                        .append("</boolProp>\n");

                // 请求参数/请求体都放在 HTTPsampler.Arguments 里（作为 HTTPSamplerProxy 的属性）
                List<Map<String, Object>> queryParams = kvList(step.get("queryParams"));
                String body = str(step.getOrDefault("body", ""));
                boolean hasBody = !body.isBlank();
                sb.append("        <elementProp name=\"HTTPsampler.Arguments\" elementType=\"Arguments\"")
                        .append(hasBody ? " guiclass=\"HTTPArgumentsPanel\"" : "")
                        .append(" testclass=\"Arguments\" testname=\"用户定义的变量\">\n");
                sb.append("          <collectionProp name=\"Arguments.arguments\">\n");
                for (Map<String, Object> q : queryParams) {
                    sb.append("            <elementProp name=\"")
                            .append(escape(str(q.get("key")))).append("\" elementType=\"HTTPArgument\">\n");
                    sb.append("              <boolProp name=\"HTTPArgument.always_encode\">false</boolProp>\n");
                    sb.append("              <stringProp name=\"Argument.name\">")
                            .append(escape(str(q.get("key")))).append("</stringProp>\n");
                    sb.append("              <stringProp name=\"Argument.value\">")
                            .append(escape(str(q.get("value")))).append("</stringProp>\n");
                    sb.append("              <stringProp name=\"Argument.metadata\">=</stringProp>\n");
                    sb.append("              <boolProp name=\"HTTPArgument.use_equals\">true</boolProp>\n");
                    sb.append("            </elementProp>\n");
                }
                if (hasBody) {
                    sb.append("            <elementProp name=\"\" elementType=\"HTTPArgument\">\n");
                    sb.append("              <boolProp name=\"HTTPArgument.always_encode\">false</boolProp>\n");
                    sb.append("              <stringProp name=\"Argument.value\">")
                            .append(escape(body)).append("</stringProp>\n");
                    sb.append("              <stringProp name=\"Argument.metadata\">=</stringProp>\n");
                    sb.append("            </elementProp>\n");
                }
                sb.append("          </collectionProp>\n        </elementProp>\n");
                if (hasBody) {
                    sb.append("        <boolProp name=\"HTTPSampler.postBodyRaw\">true</boolProp>\n");
                }
                sb.append("      </HTTPSamplerProxy>\n");

                // HeaderManager 作为 HTTPSamplerProxy 的子节点（放在 hashTree 里）
                List<Map<String, Object>> headers = kvList(step.get("headers"));
                boolean hasChildren = !headers.isEmpty() || c.getThinkTime() > 0;
                if (hasChildren) {
                    sb.append("      <hashTree>\n");
                    if (!headers.isEmpty()) {
                        sb.append("        <HeaderManager guiclass=\"HeaderPanel\" testclass=\"HeaderManager\" ")
                                .append("testname=\"HTTP信息头管理器\">\n");
                        sb.append("          <collectionProp name=\"HeaderManager.headers\">\n");
                        for (Map<String, Object> h : headers) {
                            sb.append("            <elementProp name=\"\" elementType=\"Header\">\n");
                            sb.append("              <stringProp name=\"Header.name\">")
                                    .append(escape(str(h.get("key")))).append("</stringProp>\n");
                            sb.append("              <stringProp name=\"Header.value\">")
                                    .append(escape(str(h.get("value")))).append("</stringProp>\n");
                            sb.append("            </elementProp>\n");
                        }
                        sb.append("          </collectionProp>\n        </HeaderManager>\n        <hashTree/>\n");
                    }
                    if (c.getThinkTime() > 0) {
                        sb.append("        <ConstantTimer guiclass=\"ConstantTimerGui\" ")
                                .append("testclass=\"ConstantTimer\" testname=\"思考时间\">\n");
                        sb.append("          <stringProp name=\"ConstantTimer.delay\">")
                                .append(c.getThinkTime()).append("</stringProp>\n");
                        sb.append("        </ConstantTimer>\n        <hashTree/>\n");
                    }
                    sb.append("      </hashTree>\n");
                } else {
                    sb.append("      <hashTree/>\n");
                }
            }
            sb.append(indent).append("</hashTree>\n");
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
        }

        private static String str(Object o) {
            return o == null ? "" : String.valueOf(o);
        }

        /** 把已解析的 List<Map>（或 null）安全转为 List<Map<String,Object>> */
        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> kvList(Object obj) {
            if (obj instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        result.add((Map<String, Object>) m);
                    }
                }
                return result;
            }
            return List.of();
        }
    }
}
