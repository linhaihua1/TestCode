package com.apiweb.service;

import com.apiweb.config.EngineProperties;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.RunStatus;
import com.apiweb.engine.UiSegment;
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
 *    "value":"...", "timeoutMs":<元素等待超时，缺省取配置>}
 *
 * <p>各类默认超时来自 {@link EngineProperties}（{@code apiweb.engine.ui.*}），不硬编码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiExecutionService {

    private final UiTestCaseMapper uiTestCaseMapper;
    private final UiReportMapper uiReportMapper;
    private final OssService ossService;
    private final EngineProperties engineProperties;

    /**
     * 异步执行 UI 自动化用例（由 MQ 消费者通过 @Async 调用）。
     *
     * <p>使用 {@code @Async} 的原因：Selenium 驱动真实 Chrome 属于长耗时、独占浏览器实例的
     * 操作，同步执行会阻塞 MQ 消费线程或 HTTP 请求线程；异步后由独立线程池承载，
     * 调用方可立即返回。线程池配置见 {@code ExecutorConfig}。</p>
     *
     * @param message MQ 任务消息（含 runId / caseId）
     */
    @Async
    public void executeAsync(EngineDtos.TaskMessage message) {
        execute(message.getRunId(), message.getCaseId());
    }

    /**
     * 同步执行 UI 用例（在当前线程启动 ChromeDriver 并执行 setup/test/teardown 三段）。
     *
     * <p>设计要点：
     * <ul>
     *   <li>本方法为同步阻塞，必须由 {@link #executeAsync} 调用；</li>
     *   <li>启动独立 {@code ChromeDriver} 实例，finally 中 {@code driver.quit()} 释放，
     *       避免无头浏览器进程泄漏；</li>
     *   <li>单步异常会被捕获并记入明细，不影响后续步骤；整体状态按明细是否有非 SUCCESS 决定。</li>
     * </ul>
     *
     * <p>副作用：更新 {@code t_ui_report} 的 status / duration / details（明细超过阈值时经
     * {@link OssService} 转存 OSS，库内仅存 {@code minio://} 引用）并落库。</p>
     *
     * @param runId  本次执行的报告 ID（对应 UiReportEntity.id）
     * @param caseId UI 用例 ID（对应 UiTestCaseEntity.id）
     */
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
        String status = RunStatus.SUCCESS.getCode();
        try {
            EngineProperties.Ui ui = engineProperties.getUi();
            ChromeOptions options = new ChromeOptions();
            List<String> args = new ArrayList<>();
            if (ui.isHeadless()) {
                args.add("--headless=new");
            }
            // 容器 / 无特权环境必需参数，保持常开
            args.add("--no-sandbox");
            args.add("--disable-gpu");
            args.add("--window-size=" + ui.getWindowSize());
            options.addArguments(args);

            // 驱动路径解析顺序：环境变量 WEBDRIVER_CHROME_DRIVER > JVM 系统属性 > 配置文件
            String driverPath = System.getenv("WEBDRIVER_CHROME_DRIVER");
            if (driverPath == null || driverPath.isBlank()) {
                driverPath = System.getProperty("webdriver.chrome.driver");
            }
            if (driverPath == null || driverPath.isBlank()) {
                driverPath = ui.getDriverPath();
            }
            if (driverPath != null && !driverPath.isBlank()) {
                System.setProperty("webdriver.chrome.driver", driverPath);
            }
            driver = new ChromeDriver(options);
            // 页面加载超时 + 隐式等待兜底（具体交互步骤另有显式等待 + 重试）
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(ui.getPageLoadTimeoutSeconds()));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(ui.getImplicitWaitSeconds()));

            runSegment(driver, test.getSetupSteps(), UiSegment.SETUP.getCode(), details);
            runSegment(driver, test.getSteps(), UiSegment.TEST.getCode(), details);
            runSegment(driver, test.getTeardownSteps(), UiSegment.TEARDOWN.getCode(), details);

            if (details.stream().anyMatch(
                    d -> !RunStatus.SUCCESS.getCode().equals(d.get("status")))) {
                status = RunStatus.FAILED.getCode();
            }
        } catch (Exception e) {
            status = RunStatus.ERROR.getCode();
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("segment", UiSegment.ERROR.getCode());
            err.put("status", RunStatus.ERROR.getCode());
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
                OssKeys.uiReportDetails(runId)));
        uiReportMapper.updateById(report);
    }

    /**
     * 执行某一阶段（segment）的步骤序列：setup / test / teardown。
     *
     * <p>每个步骤的成败独立记录到 details，单步失败仅标记该步骤为 FAILED 并截图，
     * 不中断后续步骤；最终报告状态由调用方汇总。</p>
     *
     * @param driver  已初始化的 WebDriver
     * @param stepsJson 该阶段的步骤 JSON（可为 null，按空数组处理）
     * @param segment 阶段标识（SETUP / TEST / TEARDOWN），用于明细分组
     * @param details 累积的步骤执行明细（出参）
     */
    @SuppressWarnings("unchecked")
    private void runSegment(WebDriver driver, String stepsJson, String segment,
                            List<Map<String, Object>> details) {
        List<Map<String, Object>> steps = JsonUtils.fromJson(stepsJson == null ? "[]" : stepsJson,
                List.class);
        for (Map<String, Object> step : steps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segment", segment);
            row.put("step", step);
            row.put("status", RunStatus.SUCCESS.getCode());
            long t0 = System.currentTimeMillis();
            try {
                doStep(driver, step, row);
            } catch (Exception e) {
                row.put("status", RunStatus.FAILED.getCode());
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
                    step.get("timeoutMs") instanceof Number n
                            ? n.longValue() : engineProperties.getUi().getWaitDefaultMs());
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
        EngineProperties.Ui ui = engineProperties.getUi();
        long timeoutMs = step.get("timeoutMs") instanceof Number n
                ? n.longValue() : ui.getElementTimeoutMs();
        Duration wait = Duration.ofMillis(Math.max(timeoutMs, ui.getMinElementTimeoutMs()));
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
