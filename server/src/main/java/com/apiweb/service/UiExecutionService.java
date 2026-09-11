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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

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
            // 允许通过环境变量指定 chromedriver 路径（解决不同环境 chromedriver 版本不匹配的问题）。
            // 优先读取 WEBDRIVER_CHROME_DRIVER，其次 system property。
            String driverPath = System.getenv("WEBDRIVER_CHROME_DRIVER");
            if (driverPath == null || driverPath.isBlank()) {
                driverPath = System.getProperty("webdriver.chrome.driver");
            }
            if (driverPath != null && !driverPath.isBlank()) {
                System.setProperty("webdriver.chrome.driver", driverPath);
            }
            driver = new ChromeDriver(options);
            // 页面加载超时 + 隐式等待兜底（具体交互步骤另有显式等待 + 重试）
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));

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
     * 8 种定位方式 → By 对象。
     */
    private By by(WebDriver driver, Map<String, Object> step) {
        String locator = str(step.get("locator"), "css");
        String selector = str(step.get("selector"), "");
        return switch (locator) {
            case "id" -> By.id(selector);
            case "name" -> By.name(selector);
            case "css" -> By.cssSelector(selector);
            case "xpath" -> By.xpath(selector);
            case "class" -> By.className(selector);
            case "tag" -> By.tagName(selector);
            case "link_text" -> By.linkText(selector);
            case "partial_link_text" -> By.partialLinkText(selector);
            default -> throw new IllegalArgumentException("未知定位方式: " + locator);
        };
    }

    /**
     * 查找元素：显式等待元素可见 + 定位失败重试，提升稳定性。
     *
     * <p>区别于 Selenium 隐式等待（只保证元素存在 DOM），这里用
     * {@link ExpectedConditions#visibilityOfElementLocated} 等待元素真正可见、
     * 可交互，更贴近真实用户操作时序。</p>
     */
    private WebElement findElement(WebDriver driver, Map<String, Object> step) {
        long timeoutMs = step.get("timeoutMs") instanceof Number n ? n.longValue() : 5000L;
        Duration wait = Duration.ofMillis(Math.max(timeoutMs, 1000L));
        By by = by(driver, step);
        return retry(() -> new WebDriverWait(driver, wait)
                .until(ExpectedConditions.visibilityOfElementLocated(by)),
                "等待元素可见超时: " + by);
    }

    /**
     * 带重试的执行：元素类操作偶发 StaleElementReference 或瞬时不可见，重试一次。
     */
    private <T> T retry(Supplier<T> action, String failMsg) {
        try {
            return action.get();
        } catch (NoSuchElementException | StaleElementReferenceException
                 | org.openqa.selenium.TimeoutException first) {
            // 瞬时失败重试一次，仍失败则抛原始异常
            return action.get();
        } catch (Exception e) {
            if (failMsg != null && (e.getMessage() == null || e.getMessage().isBlank())) {
                throw new IllegalStateException(failMsg, e);
            }
            throw e;
        }
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
