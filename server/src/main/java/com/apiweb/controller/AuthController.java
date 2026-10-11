package com.apiweb.controller;

import com.apiweb.common.Result;
import com.apiweb.dto.auth.AuthRequest;
import com.apiweb.dto.auth.ChangePasswordReq;
import com.apiweb.service.AuthService;
import com.apiweb.vo.auth.CurrentUserVO;
import com.apiweb.vo.auth.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证相关接口。
 *
 * <p>提供登录、注册、改密、当前用户信息 4 个端点。
 * 全部接口无需 token（白名单，由 {@code WebConfig} 排除 AuthInterceptor）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录接口。
     *
     * @param req 登录请求（用户名 + 密码）
     * @return token + 用户基本信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AuthRequest req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 用户注册（开放注册，新用户默认 member 角色）。
     *
     * @param req 注册请求
     * @return 无业务数据
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody AuthRequest req) {
        authService.register(req);
        return Result.ok();
    }

    /**
     * 修改当前用户密码。
     *
     * @param req 含 oldPassword / newPassword
     * @return 无业务数据
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(req);
        return Result.ok();
    }

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前用户基本信息
     */
    @GetMapping("/me")
    public Result<CurrentUserVO> me() {
        return Result.ok(authService.me());
    }
}
