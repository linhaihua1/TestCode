package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.ui.UiScenarioReq;
import com.apiweb.dto.ui.UiTestCaseReq;
import com.apiweb.service.UiService;
import com.apiweb.vo.ui.UiReportVO;
import com.apiweb.vo.ui.UiScenarioVO;
import com.apiweb.vo.ui.UiTestCaseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * UI 自动化：测试用例 / 执行场景 / 执行与报告。
 */
@RestController
@RequestMapping("/api/v1/ui")
@RequiredArgsConstructor
public class UiController {

    private final UiService uiService;

    // ---------------- UI 测试用例 ----------------

    /**
     * 查询项目的 UI 测试用例列表。
     */
    @GetMapping("/tests")
    public Result<List<UiTestCaseVO>> listTests(@RequestParam String projectId) {
        return Result.ok(uiService.listTests(projectId));
    }

    /**
     * 获取 UI 用例详情。
     */
    @GetMapping("/tests/{id}")
    public Result<UiTestCaseVO> getTest(@PathVariable String id) {
        return Result.ok(uiService.getTest(id));
    }

    /**
     * 新建 UI 用例。
     *
     * @audit 写审计日志：action=create, entityType=ui_test
     */
    @AuditLog(action = "create", entityType = "ui_test")
    @PostMapping("/tests")
    public Result<UiTestCaseVO> createTest(@Valid @RequestBody UiTestCaseReq req) {
        return Result.ok(uiService.createTest(req));
    }

    /**
     * 更新 UI 用例。
     *
     * @audit 写审计日志：action=update, entityType=ui_test
     */
    @AuditLog(action = "update", entityType = "ui_test")
    @PutMapping("/tests/{id}")
    public Result<Void> updateTest(@PathVariable String id, @Valid @RequestBody UiTestCaseReq req) {
        uiService.updateTest(id, req);
        return Result.ok();
    }

    /**
     * 删除 UI 用例。
     *
     * @audit 写审计日志：action=delete, entityType=ui_test
     */
    @AuditLog(action = "delete", entityType = "ui_test")
    @DeleteMapping("/tests/{id}")
    public Result<Void> deleteTest(@PathVariable String id) {
        uiService.deleteTest(id);
        return Result.ok();
    }

    // ---------------- UI 执行场景 ----------------

    /**
     * 查询项目的 UI 执行场景列表。
     */
    @GetMapping("/scenarios")
    public Result<List<UiScenarioVO>> listScenarios(@RequestParam String projectId) {
        return Result.ok(uiService.listScenarios(projectId));
    }

    /**
     * 获取 UI 场景详情（含步骤列表）。
     */
    @GetMapping("/scenarios/{id}")
    public Result<Map<String, Object>> getScenario(@PathVariable String id) {
        return Result.ok(uiService.getScenario(id));
    }

    /**
     * 新建 UI 场景。
     *
     * @audit 写审计日志：action=create, entityType=ui_scenario
     */
    @AuditLog(action = "create", entityType = "ui_scenario")
    @PostMapping("/scenarios")
    public Result<UiScenarioVO> createScenario(@RequestBody UiScenarioReq req) {
        return Result.ok(uiService.createScenario(req));
    }

    /**
     * 更新 UI 场景。
     *
     * @audit 写审计日志：action=update, entityType=ui_scenario
     */
    @AuditLog(action = "update", entityType = "ui_scenario")
    @PutMapping("/scenarios/{id}")
    public Result<Void> updateScenario(@PathVariable String id, @RequestBody UiScenarioReq req) {
        uiService.updateScenario(id, req);
        return Result.ok();
    }

    /**
     * 删除 UI 场景。
     *
     * @audit 写审计日志：action=delete, entityType=ui_scenario
     */
    @AuditLog(action = "delete", entityType = "ui_scenario")
    @DeleteMapping("/scenarios/{id}")
    public Result<Void> deleteScenario(@PathVariable String id) {
        uiService.deleteScenario(id);
        return Result.ok();
    }

    // ---------------- 执行与报告 ----------------

    /**
     * 触发 UI 用例执行。
     *
     * @audit 写审计日志：action=execute, entityType=ui_test
     */
    @AuditLog(action = "execute", entityType = "ui_test")
    @PostMapping("/tests/{id}/run")
    public Result<UiReportVO> runTest(@PathVariable String id) {
        return Result.ok(uiService.runTest(id));
    }

    /**
     * 触发 UI 场景执行。
     *
     * @audit 写审计日志：action=execute, entityType=ui_scenario
     */
    @AuditLog(action = "execute", entityType = "ui_scenario")
    @PostMapping("/scenarios/{id}/run")
    public Result<List<UiReportVO>> runScenario(@PathVariable String id) {
        return Result.ok(uiService.runScenario(id));
    }

    /**
     * 查询项目的 UI 报告列表。
     */
    @GetMapping("/reports")
    public Result<List<UiReportVO>> listReports(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "${apiweb.engine.list-max-limit:100}") int limit) {
        return Result.ok(uiService.listReports(projectId, limit));
    }

    /**
     * 获取 UI 报告详情。
     */
    @GetMapping("/reports/{id}")
    public Result<UiReportVO> getReport(@PathVariable String id) {
        return Result.ok(uiService.getReport(id));
    }

    /**
     * 删除 UI 报告。
     */
    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable String id) {
        uiService.deleteReport(id);
        return Result.ok();
    }
}
