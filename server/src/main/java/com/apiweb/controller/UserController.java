package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.UserEntity;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.UserContext;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public Result<List<UserEntity>> list() {
        requireAdmin();
        List<UserEntity> users = userMapper.selectList(null);
        users.forEach(u -> u.setPasswordHash(null));
        return Result.ok(users);
    }

    @AuditLog(action = "create", entityType = "user")
    @PostMapping
    public Result<UserEntity> create(@RequestBody Map<String, String> body) {
        requireAdmin();
        String username = body.get("username");
        String password = body.get("password");
        String role = body.getOrDefault("role", "member");
        if (username == null || username.isBlank() || password == null || password.length() < 6) {
            throw BizException.badRequest("用户名必填，密码至少 6 位");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        userMapper.insert(user);
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    @AuditLog(action = "update", entityType = "user")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        requireAdmin();
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (body.containsKey("role")) {
            user.setRole(body.get("role"));
        }
        if (body.containsKey("password")) {
            String pwd = body.get("password");
            if (pwd != null && pwd.length() >= 6) {
                user.setPasswordHash(passwordEncoder.encode(pwd));
            }
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    @AuditLog(action = "delete", entityType = "user")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        requireAdmin();
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
