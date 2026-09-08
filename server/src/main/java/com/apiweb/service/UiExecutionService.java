package com.apiweb.service;

import com.apiweb.engine.EngineDtos;
import com.apiweb.entity.UiReportEntity;
import com.apiweb.entity.UiTestCaseEntity;
import com.apiweb.mapper.UiReportMapper;
import com.apiweb.mapper.UiTestCaseMapper;
import com.apiweb.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI 自动化执行服务：基于 Selenium 驱动真实 Chrome。
 * 步骤 JSON 约定（8 种定位方式）：
 *   {"action":"open|click|input|clear|submit|wait|assert_text|assert_title|screenshot|script",
 *    "locator":"id|name|css|xpath|class|tag|link_text|partial_link_text", "selector":"...",
 *    "value":"...", "timeoutMs":5000}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiExecutionService {

    private final UiTestCaseMapper uiTestCaseMapper;
    private final UiReportMapper uiReportMapper;
    private final OssService ossService;

    @Async
    public void executeAsync(EngineDtos.TaskMessage message) {
        execute(message.getRunId(), message.getCaseId());
    }

    public void execute(String runId, String caseId) {
        UiTestCaseEntity test = uiTestCaseMapper.selectById(caseId);
        UiReportEntity report = uiReportMapper.selectById(runId);
        if (test == null || report == null) {
            log.warn("UI 用例或报告不存在: caseId={}, runId={}", caseId, runId);
            return;
        }
        long start = System.currentTimeMillis();
        WebDriver driver = null;
        List<Map<String, Object>> details = new ArrayList<>();
        String status = "success";
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new", "--no-sandbox", "--disable-gpu",
                    "--window-size=1920,1080");
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

            runSegment(driver, test.getSetupSteps(), "setup", details);
            runSegment(driver, test.getSteps(), "test", details);
            runSegment(driver, test.getTeardownSteps(), "teardown", details);

            if (details.stream().anyMatch(d -> !"success".equals(d.get("status")))) {
                status = "failed";
            }
        } catch (Exception e) {
            status = "error";
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("segment", "error");
            err.put("status", "error");
            err.put("message", e.getMessage());
            details.add(err);
            log.error("UI 用例执行异常", e);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
        }
        report.setStatus(status);
        report.setDuration((int) (System.currentTimeMillis() - start));
        report.setDetails(ossService.putIfLarge(JsonUtils.toJson(details),
                "ui-reports/" + runId + "/details.json"));
        uiReportMapper.updateById(report);
    }

    @SuppressWarnings("unchecked")
    private void runSegment(WebDriver driver, String stepsJson, String segment,
                            List<Map<String, Object>> details) {
        List<Map<String, Object>> steps = JsonUtils.fromJson(stepsJson == null ? "[]" : stepsJson,
                List.class);
        for (Map<String, Object> step : steps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segment", segment);
            row.put("step", step);
            row.put("status", "success");
            long t0 = System.currentTimeMillis();
            try {
                doStep(driver, step, row);
            } catch (Exception e) {
                row.put("status", "failed");
                row.put("message", e.getMessage());
                takeScreenshot(driver, row);
            }
            row.put("durationMs", System.currentTimeMillis() - t0);
            details.add(row);
        }
    }

    private void doStep(WebDriver driver, Map<String, Object> step, Map<String, Object> row) throws InterruptedException {
        String action = str(step.get("action"), "open");
        WebElement el = action.equals("open") || action.equals("wait") || action.equals("script")
                || action.startsWith("assert_title") ? null : findElement(driver, step);
        switch (action) {
            case "open" -> driver.get(str(step.get("value"), "about:blank"));
            case "click" -> el.click();
            case "input" -> {
                el.clear();
                el.sendKeys(str(step.get("value"), ""));
            }
            case "clear" -> el.clear();
            case "submit" -> el.submit();
            case "wait" -> Thread.sleep(
                    step.get("timeoutMs") instanceof Number n ? n.longValue() : 1000L);
            case "assert_text" -> {
                String expected = str(step.get("value"), "");
                if (el != null && !el.getText().contains(expected)) {
                    throw new AssertionError("文本断言失败: 期望包含 '" + expected
                            + "'，实际 '" + el.getText() + "'");
                }
            }
            case "assert_title" -> {
                String expected = str(step.get("value"), "");
                if (!driver.getTitle().contains(expected)) {
                    throw new AssertionError("标题断言失败: 期望包含 '" + expected
                            + "'，实际 '" + driver.getTitle() + "'");
                }
            }
            case "script" -> ((JavascriptExecutor) driver).executeScript(str(step.get("value"), ""));
            default -> throw new IllegalArgumentException("未知 UI 步骤动作: " + action);
        }
    }

    /**
     * 8 种定位方式。
     */
    private WebElement findElement(WebDriver driver, Map<String, Object> step) {
        By by = switch (str(step.get("locator"), "css")) {
            case "id" -> By.id(str(step.get("selector"), ""));
            case "name" -> By.name(str(step.get("selector"), ""));
            case "css" -> By.cssSelector(str(step.get("selector"), ""));
            case "xpath" -> By.xpath(str(step.get("selector"), ""));
            case "class" -> By.className(str(step.get("selector"), ""));
            case "tag" -> By.tagName(str(step.get("selector"), ""));
            case "link_text" -> By.linkText(str(step.get("selector"), ""));
            case "partial_link_text" -> By.partialLinkText(str(step.get("selector"), ""));
            default -> throw new IllegalArgumentException("未知定位方式");
        };
        return driver.findElement(by);
    }

    /**
     * 失败自动截图：截图 base64 暂存明细中（明细超 1MB 由 OssService 统一转存）。
     */
    private void takeScreenshot(WebDriver driver, Map<String, Object> row) {
        try {
            if (driver instanceof TakesScreenshot ts) {
                byte[] png = ts.getScreenshotAs(OutputType.BYTES);
                row.put("screenshot", java.util.Base64.getEncoder().encodeToString(png));
            }
        } catch (Exception ignored) {
        }
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }
}
