package com.apiweb.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引擎层枚举契约测试。
 *
 * <p>这些枚举取代了原先散落在各处的字符串字面量，其 {@code code} 是**前后端 / 数据库的稳定契约**。
 * 本测试锁死取值，防止后续重构无意改坏协议。
 */
class EngineEnumsTest {

    // ---------------- RunStatus ----------------

    @Test
    @DisplayName("RunStatus: 编码与前后端契约一致")
    void runStatusCodes() {
        assertEquals("pending", RunStatus.PENDING.getCode());
        assertEquals("running", RunStatus.RUNNING.getCode());
        assertEquals("success", RunStatus.SUCCESS.getCode());
        assertEquals("failed", RunStatus.FAILED.getCode());
        assertEquals("error", RunStatus.ERROR.getCode());
        assertEquals("skipped", RunStatus.SKIPPED.getCode());
    }

    @Test
    @DisplayName("RunStatus: 编码唯一")
    void runStatusCodesUnique() {
        assertUniqueCodes(RunStatus.values());
    }

    @Test
    @DisplayName("RunStatus.parse: 大小写不敏感，无法识别返回 null")
    void runStatusParse() {
        assertSame(RunStatus.SUCCESS, RunStatus.parse("success"));
        assertSame(RunStatus.SUCCESS, RunStatus.parse("SUCCESS"));
        assertSame(RunStatus.SUCCESS, RunStatus.parse("Success"));
        assertSame(RunStatus.SKIPPED, RunStatus.parse("skipped"));
        assertNull(RunStatus.parse("not-a-status"));
        assertNull(RunStatus.parse(null));
        assertNull(RunStatus.parse("  "));
    }

    @Test
    @DisplayName("RunStatus.isFailure: 仅 failed / error 视为未通过")
    void runStatusIsFailure() {
        assertTrue(RunStatus.isFailure("failed"));
        assertTrue(RunStatus.isFailure("error"));
        assertFalse(RunStatus.isFailure("success"));
        assertFalse(RunStatus.isFailure("skipped"));
        assertFalse(RunStatus.isFailure("pending"));
        assertFalse(RunStatus.isFailure(null));
    }

    // ---------------- ReportStatus ----------------

    @Test
    @DisplayName("ReportStatus: 编码与 t_report.status 契约一致")
    void reportStatusCodes() {
        assertEquals("running", ReportStatus.RUNNING.getCode());
        assertEquals("passed", ReportStatus.PASSED.getCode());
        assertEquals("failed", ReportStatus.FAILED.getCode());
        assertEquals("error", ReportStatus.ERROR.getCode());
        assertEquals("passed", ReportStatus.PASSED.getCode());
    }

    @Test
    @DisplayName("ReportStatus.resolve: 异常优先于失败，零用例视为通过")
    void reportStatusResolve() {
        assertSame(ReportStatus.PASSED, ReportStatus.resolve(0, 0, 0));
        assertSame(ReportStatus.PASSED, ReportStatus.resolve(0, 0, 5));
        assertSame(ReportStatus.FAILED, ReportStatus.resolve(0, 1, 5));
        assertSame(ReportStatus.ERROR, ReportStatus.resolve(1, 0, 5));
        // 同时存在异常与失败时，异常优先
        assertSame(ReportStatus.ERROR, ReportStatus.resolve(1, 3, 5));
    }

    // ---------------- TriggerType ----------------

    @Test
    @DisplayName("TriggerType: 编码与 t_report.trigger_type 契约一致")
    void triggerTypeCodes() {
        assertEquals("manual", TriggerType.MANUAL.getCode());
        assertEquals("schedule", TriggerType.SCHEDULE.getCode());
        assertEquals("webhook", TriggerType.WEBHOOK.getCode());
        assertEquals("api", TriggerType.API.getCode());
    }

    @Test
    @DisplayName("TriggerType.parse: 大小写不敏感，无法识别返回 null")
    void triggerTypeParse() {
        assertSame(TriggerType.WEBHOOK, TriggerType.parse("webhook"));
        assertSame(TriggerType.API, TriggerType.parse("API"));
        assertNull(TriggerType.parse("unknown"));
        assertNull(TriggerType.parse(null));
    }

    // ---------------- FailStrategy ----------------

    @Test
    @DisplayName("FailStrategy: 编码与前端下拉项契约一致")
    void failStrategyCodes() {
        assertEquals("stop_on_fail", FailStrategy.STOP_ON_FAIL.getCode());
        assertEquals("continue_all", FailStrategy.CONTINUE_ALL.getCode());
        assertEquals("retry_then_stop", FailStrategy.RETRY_THEN_STOP.getCode());
        assertSame(FailStrategy.STOP_ON_FAIL, FailStrategy.DEFAULT);
    }

    @Test
    @DisplayName("FailStrategy.parseOrDefault: 无法识别时回退默认策略")
    void failStrategyParseOrDefault() {
        assertSame(FailStrategy.CONTINUE_ALL, FailStrategy.parseOrDefault("continue_all"));
        assertSame(FailStrategy.RETRY_THEN_STOP, FailStrategy.parseOrDefault("RETRY_THEN_STOP"));
        assertSame(FailStrategy.STOP_ON_FAIL, FailStrategy.parseOrDefault("bogus"));
        assertSame(FailStrategy.STOP_ON_FAIL, FailStrategy.parseOrDefault(null));
    }

    @Test
    @DisplayName("FailStrategy.stopOnFailure: 停止类策略判定")
    void failStrategyStopOnFailure() {
        assertTrue(FailStrategy.STOP_ON_FAIL.stopOnFailure());
        assertTrue(FailStrategy.RETRY_THEN_STOP.stopOnFailure());
        assertFalse(FailStrategy.CONTINUE_ALL.stopOnFailure());
    }

    // ---------------- ExecuteMode ----------------

    @Test
    @DisplayName("ExecuteMode: 编码与 t_test_task.execute_mode 契约一致")
    void executeModeCodes() {
        assertEquals("sequential", ExecuteMode.SEQUENTIAL.getCode());
        assertEquals("parallel", ExecuteMode.PARALLEL.getCode());
        assertSame(ExecuteMode.SEQUENTIAL, ExecuteMode.DEFAULT);
    }

    @Test
    @DisplayName("ExecuteMode.parseOrDefault: 无法识别时回退顺序执行")
    void executeModeParseOrDefault() {
        assertSame(ExecuteMode.PARALLEL, ExecuteMode.parseOrDefault("parallel"));
        assertSame(ExecuteMode.PARALLEL, ExecuteMode.parseOrDefault("PARALLEL"));
        assertSame(ExecuteMode.SEQUENTIAL, ExecuteMode.parseOrDefault("whatever"));
        assertSame(ExecuteMode.SEQUENTIAL, ExecuteMode.parseOrDefault(null));
    }

    // ---------------- TaskType ----------------

    @Test
    @DisplayName("TaskType: 编码与 MQ 路由 / 执行机能力标签契约一致")
    void taskTypeCodes() {
        assertEquals("api", TaskType.API.getCode());
        assertEquals("ui", TaskType.UI.getCode());
        assertEquals("perf", TaskType.PERF.getCode());
    }

    @Test
    @DisplayName("TaskType.parse: 大小写不敏感，无法识别返回 null")
    void taskTypeParse() {
        assertSame(TaskType.API, TaskType.parse("api"));
        assertSame(TaskType.UI, TaskType.parse("UI"));
        assertSame(TaskType.PERF, TaskType.parse("Perf"));
        assertNull(TaskType.parse("nope"));
        assertNull(TaskType.parse(null));
    }

    // ---------------- ExecutorStatus / UiSegment ----------------

    @Test
    @DisplayName("ExecutorStatus: 编码与 t_executor_node.status 契约一致")
    void executorStatusCodes() {
        assertEquals("online", ExecutorStatus.ONLINE.getCode());
        assertEquals("busy", ExecutorStatus.BUSY.getCode());
        assertEquals("offline", ExecutorStatus.OFFLINE.getCode());
        assertUniqueCodes(ExecutorStatus.values());
    }

    @Test
    @DisplayName("UiSegment: 编码与 UI 执行明细 segment 契约一致")
    void uiSegmentCodes() {
        assertEquals("setup", UiSegment.SETUP.getCode());
        assertEquals("test", UiSegment.TEST.getCode());
        assertEquals("teardown", UiSegment.TEARDOWN.getCode());
        assertEquals("error", UiSegment.ERROR.getCode());
    }

    // ---------------- 工具 ----------------

    /** 断言所有枚举项的 code 互不相同（防止复制粘贴导致协议冲突）。 */
    private static void assertUniqueCodes(RunStatus[] values) {
        assertUnique(Arrays.stream(values).map(RunStatus::getCode).toList());
    }

    private static void assertUniqueCodes(ExecutorStatus[] values) {
        assertUnique(Arrays.stream(values).map(ExecutorStatus::getCode).toList());
    }

    private static void assertUnique(java.util.List<String> codes) {
        Set<String> set = new HashSet<>(codes);
        assertEquals(codes.size(), set.size(), "枚举 code 出现重复: " + codes);
    }
}
