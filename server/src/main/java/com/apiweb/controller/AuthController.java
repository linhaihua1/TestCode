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
 * 认证相关接口。
 *
 * <p>提供登录、注册、改密、当前用户信息 4 个端点。
 * 全部接口无需 token（白名单，由 {@code WebConfig} 排除 AuthInterceptor）。
 *
 * <h3>密码安全</h3>
 * <ul>
 *   <li>密码用 BCrypt 哈希存储（{@code BCryptPasswordEncoder}），不存明文</li>
 *   <li>登录比对：{@code matches(rawPassword, hashedPassword)}</li>
 *   <li>注册/改密：{@code encode(rawPassword)}</li>
 * </ul>
 *
 * <h3>错误码约定</h3>
 * <ul>
 *   <li>用户名/密码错误 → 401 BizException</li>
 *   <li>用户名已存在 → 400 BizException</li>
 *   <li>原密码错误 → 400 BizException</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 登录请求体：用户名 + 密码。
     */
    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    /**
     * 登录接口。
     *
     * @param req 登录请求（用户名 + 密码）
     * @return 成功返回 token + 用户基本信息；失败抛 BizException(401)
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated LoginRequest req) {
        UserEntity user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            // 故意不区分"用户不存在"与"密码错"，避免账号枚举攻击
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

    /**
     * 用户注册（开放注册，生产环境可禁用或加权限）。
     *
     * <p>新用户默认角色为 {@code member}（普通成员），无管理权限。</p>
     */
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

    /**
     * 修改当前用户密码。
     *
     * @param body 含 {@code oldPassword} / {@code newPassword} 两个字段
     *             newPassword 至少 6 位
     */
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

    /**
     * 获取当前登录用户信息（前端刷新页面时验证 token 有效性）。
     */
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
