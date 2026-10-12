package com.apiweb.service;

import com.apiweb.config.EngineProperties;
import com.apiweb.engine.RunStatus;
import com.apiweb.mapper.UiReportMapper;
import com.apiweb.mapper.UiTestCaseMapper;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UI 执行服务的步骤级契约测试。
 *
 * <p>重点锁死一个真实缺陷（回归防护）：断言失败抛的是 {@link AssertionError}（属于 {@code Error}
 * 而非 {@code Exception}），若步骤循环只捕获 {@code Exception}，异常会逃逸出 {@code @Async}
 * 方法，导致 {@code t_ui_report} 永久停留 {@code pending}、执行机线程池被错误吞掉。
 * 因此这里断言：断言失败必须被捕获、记入明细 status=failed，且不向上抛出。
 */
class UiExecutionServiceTest {

    /** 构造被测服务：仅 steps 解析逻辑被覆盖，4 个依赖用 mock/默认实例即可。 */
    private UiExecutionService newService() {
        return new UiExecutionService(
                mock(UiTestCaseMapper.class),
                mock(UiReportMapper.class),
                mock(OssService.class),
                new EngineProperties());
    }

    @Test
    void 断言失败应记为failed且不向上抛出() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getTitle()).thenReturn("Example Domain");

        List<Map<String, Object>> details = new ArrayList<>();
        String steps = "[{\"action\":\"assert_title\",\"value\":\"NONEXISTENT_XYZ\",\"name\":\"断言标题\"}]";

        assertDoesNotThrow(() ->
                newService().runSegment(driver, steps, "test", details));

        assertEquals(1, details.size(), "应有 1 条步骤明细");
        Map<String, Object> row = details.get(0);
        assertEquals(RunStatus.FAILED.getCode(), row.get("status"), "断言失败应记为 failed");
        assertTrue(String.valueOf(row.get("message")).contains("标题断言失败"),
                "明细应包含断言失败原因，实际: " + row.get("message"));
        assertTrue(row.get("durationMs") instanceof Long, "应记录耗时");
    }

    @Test
    void 断言通过应记为success() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getTitle()).thenReturn("Example Domain");

        List<Map<String, Object>> details = new ArrayList<>();
        String steps = "[{\"action\":\"assert_title\",\"value\":\"Example Domain\"}]";

        newService().runSegment(driver, steps, "test", details);

        assertEquals(1, details.size());
        assertEquals(RunStatus.SUCCESS.getCode(), details.get(0).get("status"));
    }

    @Test
    void 单步断言失败不应中断后续步骤() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.getTitle()).thenReturn("Example Domain");

        List<Map<String, Object>> details = new ArrayList<>();
        // 第 1 步断言不匹配（失败），第 2 步期望空串（必然通过）
        String steps = "[{\"action\":\"assert_title\",\"value\":\"NOPE\"},"
                + "{\"action\":\"assert_title\",\"value\":\"\"}]";

        assertDoesNotThrow(() ->
                newService().runSegment(driver, steps, "test", details));

        assertEquals(2, details.size(), "单步失败不应中断后续步骤");
        assertEquals(RunStatus.FAILED.getCode(), details.get(0).get("status"));
        assertEquals(RunStatus.SUCCESS.getCode(), details.get(1).get("status"));
    }

    @Test
    void open动作应记为success() {
        WebDriver driver = mock(WebDriver.class);

        List<Map<String, Object>> details = new ArrayList<>();
        newService().runSegment(driver, "[{\"action\":\"open\",\"value\":\"about:blank\"}]",
                "setup", details);

        assertEquals(1, details.size());
        assertEquals(RunStatus.SUCCESS.getCode(), details.get(0).get("status"));
    }
}
