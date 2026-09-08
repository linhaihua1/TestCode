package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.GlobalVariableEntity;
import com.apiweb.mapper.GlobalVariableMapper;
import com.apiweb.security.AesGcm;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private static final String TYPE_SECRET = "SECRET";
    private static final String TYPE_RANDOM = "RANDOM";
    private static final String TYPE_TIMESTAMP = "TIMESTAMP";

    private final GlobalVariableMapper globalVariableMapper;

    @GetMapping
    public Result<List<GlobalVariableEntity>> list(@RequestParam String projectId) {
        List<GlobalVariableEntity> vars = globalVariableMapper.selectList(
                new LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId)
                        .orderByAsc(GlobalVariableEntity::getName));
        // SECRET 变量前端只能看掩码
        for (GlobalVariableEntity v : vars) {
            if (TYPE_SECRET.equalsIgnoreCase(v.getType())) {
                v.setValue("**********");
            }
        }
        return Result.ok(vars);
    }

    /** 内部使用：拿到解密后的明文（用例执行时由 VariableMerger 调用） */
    public String resolveValue(String varName, String projectId) {
        GlobalVariableEntity v = globalVariableMapper.selectOne(
                new LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId)
                        .eq(GlobalVariableEntity::getName, varName));
        if (v == null) return null;
        if (TYPE_SECRET.equalsIgnoreCase(v.getType())) {
            try {
                return AesGcm.decrypt(v.getValue());
            } catch (Exception e) {
                throw BizException.badRequest("密钥解密失败: " + varName);
            }
        }
        return v.getValue();
    }

    @AuditLog(action = "create", entityType = "global_variable")
    @PostMapping
    public Result<GlobalVariableEntity> create(@RequestBody GlobalVariableEntity variable) {
        validateAndPrepare(variable);
        variable.setValue(encryptIfSecret(variable));
        globalVariableMapper.insert(variable);
        // 返回前脱敏
        if (TYPE_SECRET.equalsIgnoreCase(variable.getType())) {
            variable.setValue("**********");
        }
        return Result.ok(variable);
    }

    @AuditLog(action = "update", entityType = "global_variable")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody GlobalVariableEntity variable) {
        validateAndPrepare(variable);
        if (TYPE_SECRET.equalsIgnoreCase(variable.getType())) {
            // 保留原密文：前端只传掩码,不应重新加密掩码
            GlobalVariableEntity origin = globalVariableMapper.selectById(id);
            if (origin == null) throw BizException.notFound("变量不存在");
            variable.setValue(origin.getValue());
        } else {
            variable.setValue(encryptIfSecret(variable));
        }
        variable.setId(id);
        globalVariableMapper.updateById(variable);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "global_variable")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        globalVariableMapper.deleteById(id);
        return Result.ok();
    }

    private void validateAndPrepare(GlobalVariableEntity v) {
        if (v.getName() == null || v.getName().isBlank()) {
            throw BizException.badRequest("变量名必填");
        }
        // TIMESTAMP / RANDOM 类型由后端生成,忽略前端传来的值
        if (TYPE_TIMESTAMP.equalsIgnoreCase(v.getType())) {
            v.setValue(String.valueOf(System.currentTimeMillis()));
        } else if (TYPE_RANDOM.equalsIgnoreCase(v.getType())) {
            v.setValue(String.valueOf(System.nanoTime() ^ System.currentTimeMillis()));
        }
    }

    private String encryptIfSecret(GlobalVariableEntity v) {
        if (!TYPE_SECRET.equalsIgnoreCase(v.getType())) return v.getValue();
        if (v.getValue() == null || v.getValue().isBlank()) return "";
        if (AesGcm.isCipherText(v.getValue())) return v.getValue();  // 已是密文
        return AesGcm.encrypt(v.getValue());
    }
}