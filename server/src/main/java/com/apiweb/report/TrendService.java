package com.apiweb.report;

import com.apiweb.entity.ReportEntity;
import com.apiweb.mapper.ReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 测试趋势统计。
 *
 * <h3>核心指标</h3>
 * <ul>
 *   <li>每日用例总数、通过、失败、异常、跳过</li>
 *   <li>每日断言总数、通过、失败</li>
 *   <li>每日平均响应时间、P95 响应时间</li>
 *   <li>通过率（passed / totalCases）</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   List<TrendBucket> trend = trendService.compute(projectId, 30);
 *   // 渲染为 ECharts 折线图
 * }</pre>
 */
@Service
@RequiredArgsConstructor
public class TrendService {

    private final ReportMapper reportMapper;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 计算最近 N 天的趋势（按报告 startedAt 自然日聚合）。
     *
     * <p>无报告的日期补 0,保证前端图表 X 轴连续。
     */
    public List<TrendBucket> compute(String projectId, int days) {
        if (days < 1) days = 7;
        if (days > 365) days = 365;
        Instant from = LocalDate.now().minusDays(days - 1L)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<ReportEntity> reports = reportMapper.selectList(
                new LambdaQueryWrapper<ReportEntity>()
                        .eq(projectId != null && !projectId.isBlank(),
                                ReportEntity::getProjectId, projectId)
                        .ge(ReportEntity::getStartedAt, from)
                        .orderByAsc(ReportEntity::getStartedAt));

        // 按日聚合
        Map<String, TrendBucket> buckets = new LinkedHashMap<>();
        // 先把 N 天全部初始化为 0,保证连续
        for (int i = days - 1; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).format(DATE_FMT);
            TrendBucket b = new TrendBucket();
            b.date = key;
            buckets.put(key, b);
        }
        for (ReportEntity r : reports) {
            if (r.getStartedAt() == null) continue;
            String key = LocalDate.ofInstant(r.getStartedAt(), ZoneId.systemDefault()).format(DATE_FMT);
            TrendBucket b = buckets.computeIfAbsent(key, k -> {
                TrendBucket nb = new TrendBucket();
                nb.date = k;
                return nb;
            });
            b.totalCases += nz(r.getTotalCases());
            b.passedCases += nz(r.getPassedCases());
            b.failedCases += nz(r.getFailedCases());
            b.errorCases += nz(r.getErrorCases());
            b.skippedCases += nz(r.getSkippedCases());
            b.totalAssertions += nz(r.getTotalAssertions());
            b.passedAssertions += nz(r.getPassedAssertions());
            b.failedAssertions += nz(r.getFailedAssertions());
            b.responseTimeSum += nz(r.getAvgResponseTime());
            b.responseTimeCount++;
            if (r.getP95ResponseTime() != null && r.getP95ResponseTime() > b.p95Max) {
                b.p95Max = r.getP95ResponseTime();
            }
            b.reportCount++;
        }
        // 计算通过率 + 平均响应
        List<TrendBucket> list = new ArrayList<>(buckets.values());
        for (TrendBucket b : list) {
            b.passRate = b.totalCases == 0 ? 0
                    : Math.round((10000.0 * b.passedCases / b.totalCases)) / 100.0;
            b.avgResponseTime = b.responseTimeCount == 0 ? 0
                    : (int) (b.responseTimeSum / b.responseTimeCount);
        }
        return list;
    }

    /**
     * 最近 N 天的整体汇总（前端顶栏展示）。
     */
    public Summary summary(String projectId, int days) {
        List<TrendBucket> list = compute(projectId, days);
        Summary s = new Summary();
        for (TrendBucket b : list) {
            s.totalCases += b.totalCases;
            s.passedCases += b.passedCases;
            s.failedCases += b.failedCases;
            s.errorCases += b.errorCases;
            s.reportCount += b.reportCount;
        }
        s.passRate = s.totalCases == 0 ? 0
                : Math.round((10000.0 * s.passedCases / s.totalCases)) / 100.0;
        return s;
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    @Data
    public static class TrendBucket {
        private String date;
        private int totalCases;
        private int passedCases;
        private int failedCases;
        private int errorCases;
        private int skippedCases;
        private int totalAssertions;
        private int passedAssertions;
        private int failedAssertions;
        private int avgResponseTime;
        private int p95Max;
        private int reportCount;
        private double passRate;
        // 计算用临时字段
        private transient long responseTimeSum;
        private transient int responseTimeCount;
    }

    @Data
    public static class Summary {
        private int totalCases;
        private int passedCases;
        private int failedCases;
        private int errorCases;
        private int reportCount;
        private double passRate;
    }
}