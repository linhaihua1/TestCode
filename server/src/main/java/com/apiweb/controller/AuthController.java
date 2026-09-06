package com.apiweb.controller;

import com.apiweb.common.BizException;
import com.apiweb.common.Result;
import com.apiweb.entity.UserEntity;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.JwtUtil;
import com.apiweb.security.UserContext;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证：登录 / 注册 / 当前用户信息。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated LoginRequest req) {
        UserEntity user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return Result.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "role", user.getRole())));
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Validated LoginRequest req) {
        Long exists = userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.badRequest("用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole("member");
        userMapper.insert(user);
        return Result.ok();
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            throw BizException.badRequest("新密码长度至少 6 位");
        }
        UserEntity user = userMapper.selectById(UserContext.userId());
        if (user == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw BizException.badRequest("原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        var u = UserContext.get();
        if (u == null) {
            throw BizException.unauthorized("未登录");
        }
        return Result.ok(Map.of(
                "id", u.getUserId(),
                "username", u.getUsername(),
                "role", u.getRole()));
    }
}
