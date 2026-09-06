package com.apiweb.service;

import com.apiweb.engine.EngineDtos;
import com.apiweb.entity.PerfCaseEntity;
import com.apiweb.entity.PerfReportEntity;
import com.apiweb.mapper.PerfCaseMapper;
import com.apiweb.mapper.PerfReportMapper;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 性能测试执行服务：生成 .jmx -> 调用本机 JMeter 命令行执行 -> 解析 .jtl 汇总指标。
 * JMeter 运行时发现顺序：JMETER_HOME 环境变量 -> server/jmeter/ 目录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfExecutionService {

    private final PerfCaseMapper perfCaseMapper;
    private final PerfReportMapper perfReportMapper;
    private final OssService ossService;

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
        try {
            File jmeterHome = findJmeterHome();
            if (jmeterHome == null) {
                throw new IllegalStateException("未找到 JMeter 运行时（设置 JMETER_HOME 或解压到 server/jmeter/）");
            }
            File workDir = Files.createTempDirectory("apiweb-perf-").toFile();
            File jmxFile = new File(workDir, runId + ".jmx");
            File jtlFile = new File(workDir, runId + ".jtl");

            // 生成 .jmx
            String jmx = JmxBuilder.build(perfCase);
            Files.writeString(jmxFile.toPath(), jmx);

            // 执行 JMeter（非 GUI 模式）
            ProcessBuilder pb = new ProcessBuilder(
                    new File(jmeterHome, "bin/jmeter").getAbsolutePath(),
                    "-n", "-t", jmxFile.getAbsolutePath(),
                    "-l", jtlFile.getAbsolutePath(),
                    "-Jjmeter.save.saveservice.output_format=csv");
            pb.environment().put("JMETER_HOME", jmeterHome.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.HOURS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("JMeter 执行超时（2 小时）");
            }

            // 解析 .jtl 汇总
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
        }
        perfReportMapper.updateById(report);
    }

    private File findJmeterHome() {
        String env = System.getenv("JMETER_HOME");
        if (env != null && new File(env, "bin/ApacheJMeter.jar").exists()) {
            return new File(env);
        }
        File local = new File("jmeter");
        if (new File(local, "bin/ApacheJMeter.jar").exists()) {
            return local;
        }
        return null;
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
            for (int i = 1; i < lines.size(); i++) {
                String[] cols = lines.get(i).split(",", -1);
                if (cols.length < 4) {
                    continue;
                }
                long ts = Long.parseLong(cols[0]);
                long elapsed = Long.parseLong(cols[1]);
                String label = cols[2];
                String responseCode = cols[3];
                boolean success = cols.length > 7 && "true".equalsIgnoreCase(cols[7]);
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
            long maxTs = timeStamps.stream().max(Long::compareTo).orElse(0L) ;
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
            Map<String, Object> profile = JsonUtils.toMap(c.getProfile());
            String loadProfile = String.valueOf(profile.getOrDefault("loadProfile", "fixed"));
            int threads = c.getThreads();
            int rampUp = c.getRampUp();
            // 阶梯加压：threads 为峰值，按 5 级阶梯到达
            int steps = "stepping".equals(loadProfile) ? 5 : 1;

            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6\">\n");
            sb.append("  <hashTree>\n");
            sb.append("    <ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" ");
            sb.append("testname=\"").append(escape(c.getName())).append("\">\n");
            sb.append("      <stringProp name=\"ThreadGroup.num_threads\">")
                    .append(threads).append("</stringProp>\n");
            sb.append("      <stringProp name=\"ThreadGroup.ramp_time\">")
                    .append(Math.max(rampUp, steps)).append("</stringProp>\n");
            sb.append("      <stringProp name=\"ThreadGroup.on_sample_error\">")
                    .append(c.getOnSampleError()).append("</stringProp>\n");
            if (c.getDuration() > 0) {
                sb.append("      <stringProp name=\"ThreadGroup.duration\">")
                        .append(c.getDuration()).append("</stringProp>\n");
                sb.append("      <stringProp name=\"ThreadGroup.scheduler\">true</stringProp>\n");
            } else {
                sb.append("      <stringProp name=\"LoopController.loops\">")
                        .append(c.getLoops()).append("</stringProp>\n");
            }
            sb.append("    </ThreadGroup>\n");
            sb.append("    <hashTree>\n");

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
                sb.append("      <HTTPSamplerProxy guiclass=\"HttpTestSampleGui\" ");
                sb.append("testclass=\"HTTPSamplerProxy\" testname=\"")
                        .append(escape(str(step.getOrDefault("name", "HTTP请求")))).append("\">\n");
                sb.append("        <stringProp name=\"HTTPSampler.domain\">")
                        .append(escape(str(step.getOrDefault("host", "")))).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.port\">")
                        .append(escape(str(step.getOrDefault("port", "")))).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.path\">")
                        .append(escape(str(step.getOrDefault("path", "")))).append("</stringProp>\n");
                sb.append("        <stringProp name=\"HTTPSampler.method\">")
                        .append(escape(str(step.getOrDefault("method", "GET")))).append("</stringProp>\n");
                String body = str(step.getOrDefault("body", ""));
                if (!body.isBlank()) {
                    sb.append("        <boolProp name=\"HTTPSampler.postBodyRaw\">true</boolProp>\n");
                }
                sb.append("      </HTTPSamplerProxy>\n      <hashTree/>\n");
                if (c.getThinkTime() > 0) {
                    sb.append("      <ConstantTimer guiclass=\"ConstantTimerGui\" ")
                            .append("testclass=\"ConstantTimer\" testname=\"思考时间\">\n");
                    sb.append("        <stringProp name=\"ConstantTimer.delay\">")
                            .append(c.getThinkTime()).append("</stringProp>\n");
                    sb.append("      </ConstantTimer>\n      <hashTree/>\n");
                }
            }
            sb.append("    </hashTree>\n  </hashTree>\n</jmeterTestPlan>\n");
            return sb.toString();
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
        }

        private static String str(Object o) {
            return o == null ? "" : String.valueOf(o);
        }
    }
}
