package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.UserEntity;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.Role;
import com.apiweb.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理（仅管理员）。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 密码最小长度（配置项 apiweb.security.min-password-length，默认 6） */
    @Value("${apiweb.security.min-password-length:6}")
    private int minPasswordLength;

    /**
     * 查询全部用户列表（仅管理员）。
     * <p>返回前清空 passwordHash，避免密码哈希随响应外泄。
     *
     * @return 用户列表（不含密码哈希，role 字段标明权限）
     * @throws BizException 非管理员时抛 forbidden(403)
     */
    @GetMapping
    public Result<List<UserEntity>> list() {
        requireAdmin();
        List<UserEntity> users = userMapper.selectList(null);
        users.forEach(u -> u.setPasswordHash(null)); // 清空密码哈希，避免随响应外泄
        return Result.ok(users);
    }

    /**
     * 新建用户（仅管理员）：BCrypt 加密密码后落库，role 默认 member。
     *
     * @param body 请求体，含 username（必填）、password（至少 minPasswordLength 位）、role（可选，默认 member）
     * @return 已落库用户实体（passwordHash 已清空）
     * @throws BizException 非管理员抛 forbidden；用户名/密码不合规或角色非法时抛 badRequest
     * @audit 写审计日志：action=create, entityType=user
     */
    @AuditLog(action = "create", entityType = "user")
    @PostMapping
    public Result<UserEntity> create(@RequestBody Map<String, String> body) {
        requireAdmin();
        String username = body.get("username");
        String password = body.get("password");
        String role = body.getOrDefault("role", Role.MEMBER_CODE);
        if (username == null || username.isBlank() || password == null
                || password.length() < minPasswordLength) {
            throw BizException.badRequest("用户名必填，密码至少 " + minPasswordLength + " 位");
        }
        if (!Role.isValid(role)) {
            throw BizException.badRequest("角色非法: " + role);
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        userMapper.insert(user);
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    /**
     * 更新用户（仅管理员）：可改 role 或重置密码，未传字段保持原值。
     *
     * @param id   用户 ID（路径参数，必填）
     * @param body 请求体，可含 role、password（重置时至少 minPasswordLength 位）
     * @return 无业务数据
     * @throws BizException 非管理员抛 forbidden；用户不存在抛 notFound；角色非法抛 badRequest
     * @audit 写审计日志：action=update, entityType=user
     */
    @AuditLog(action = "update", entityType = "user")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        requireAdmin();
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (body.containsKey("role")) {
            String role = body.get("role");
            if (!Role.isValid(role)) {
                throw BizException.badRequest("角色非法: " + role);
            }
            user.setRole(role);
        }
        if (body.containsKey("password")) {
            String pwd = body.get("password");
            if (pwd != null && pwd.length() >= minPasswordLength) {
                user.setPasswordHash(passwordEncoder.encode(pwd));
            }
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    /**
     * 删除用户（仅管理员）：禁止删除当前登录用户自身（防止无管理员可用）。
     *
     * @param id 用户 ID（路径参数，必填）
     * @return 无业务数据
     * @throws BizException 非管理员抛 forbidden；删除自身时抛 badRequest
     * @audit 写审计日志：action=delete, entityType=user
     */
    @AuditLog(action = "delete", entityType = "user")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        requireAdmin();
        // 禁止管理员删除自己，避免把系统变成"无管理员可用"状态
        if (id.equals(UserContext.userId())) {
            throw BizException.badRequest("不能删除当前登录用户");
        }
        userMapper.deleteById(id);
        return Result.ok();
    }

    private void requireAdmin() {
        var user = UserContext.get();
        if (user == null || !user.isAdmin()) {
            throw BizException.forbidden("仅管理员可操作");
        }
    }
}
