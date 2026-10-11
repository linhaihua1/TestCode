package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.globalvariable.GlobalVariableReq;
import com.apiweb.service.GlobalVariableService;
import com.apiweb.vo.globalvariable.GlobalVariableVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 全局变量管理（按需求文档 §2 顶部全局配置栏 - 全局变量）。
 *
 * <h3>变量类型</h3>
 * <ul>
 *   <li>STRING：明文字符串</li>
 *   <li>NUMBER：数值</li>
 *   <li>BOOLEAN：true / false</li>
 *   <li>TIMESTAMP：自动填充为当前时间</li>
 *   <li>RANDOM：随机数（生成时随机,存储时固定）</li>
 *   <li>SECRET：密钥,数据库存密文,API 返回时仍为密文（避免泄漏）</li>
 * </ul>
 *
 * <h3>SECRET 加密</h3>
 * SECRET 类型写入数据库前使用 AES-256-GCM 加密;读取时解密回明文（仅在内部执行用例时使用）。
 * 前端只能看到掩码（如 {@code **********}）和变量名,不能看到明文值。
 */
@RestController
@RequestMapping("/api/v1/global-variables")
@RequiredArgsConstructor
public class GlobalVariableController {

    private final GlobalVariableService globalVariableService;

    /**
     * 查询项目的全局变量列表（按变量名升序）。
     *
     * @param projectId 项目 ID（必填）
     * @return 变量列表；SECRET 类型的值已替换为掩码 {@code **********}，不向外部泄漏明文
     */
    @GetMapping
    public Result<List<GlobalVariableVO>> list(@RequestParam String projectId) {
        return Result.ok(globalVariableService.list(projectId));
    }

    /**
     * 新建全局变量。
     *
     * @param req 变量请求 DTO（请求体），name 必填
     * @return 已落库的变量（SECRET 值已脱敏为掩码）
     * @audit 写审计日志：action=create, entityType=global_variable
     */
    @AuditLog(action = "create", entityType = "global_variable")
    @PostMapping
    public Result<GlobalVariableVO> create(@Valid @RequestBody GlobalVariableReq req) {
        return Result.ok(globalVariableService.create(req));
    }

    /**
     * 更新全局变量。
     *
     * @param id  变量 ID（路径参数，必填）
     * @param req 变量请求 DTO（请求体）
     * @return 无业务数据
     * @audit 写审计日志：action=update, entityType=global_variable
     */
    @AuditLog(action = "update", entityType = "global_variable")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody GlobalVariableReq req) {
        globalVariableService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除全局变量（物理删除）。
     *
     * @param id 变量 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=delete, entityType=global_variable
     */
    @AuditLog(action = "delete", entityType = "global_variable")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        globalVariableService.delete(id);
        return Result.ok();
    }
}
