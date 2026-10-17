package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.dto.auth.AuthRequest;
import com.apiweb.dto.auth.ChangePasswordReq;
import com.apiweb.entity.UserEntity;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.JwtUtil;
import com.apiweb.security.LoginGuardService;
import com.apiweb.security.Role;
import com.apiweb.security.UserContext;
import com.apiweb.vo.auth.CurrentUserVO;
import com.apiweb.vo.auth.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务服务：登录、注册、改密、当前用户信息。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginGuardService loginGuard;

    /** 密码最小长度（配置项 apiweb.security.min-password-length，默认 6） */
    @Value("${apiweb.security.min-password-length:6}")
    private int minPasswordLength;

    /**
     * 登录：校验用户名密码，签发 JWT。
     *
     * <p>防暴力破解：登录前先检查账号是否被锁定（连续失败过多）；
     * 失败计数 +1，成功清零（{@link LoginGuardService}）。</p>
     */
    public LoginVO login(AuthRequest req) {
        loginGuard.checkLocked(req.getUsername());
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            // 不区分"用户不存在"与"密码错"，避免账号枚举攻击
            loginGuard.recordFailure(req.getUsername());
            throw BizException.loginFailed("用户名或密码错误");
        }
        loginGuard.reset(req.getUsername());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        LoginVO.UserBrief brief = new LoginVO.UserBrief();
        brief.setId(user.getId());
        brief.setUsername(user.getUsername());
        brief.setRole(user.getRole());
        vo.setUser(brief);
        return vo;
    }

    /**
     * 注册：新用户默认 member 角色。
     */
    @Transactional
    public void register(AuthRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw BizException.badRequest("用户名必填");
        }
        if (req.getPassword() == null || req.getPassword().length() < minPasswordLength) {
            throw BizException.badRequest("密码长度至少 " + minPasswordLength + " 位");
        }
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.badRequest("用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(Role.MEMBER_CODE);
        userMapper.insert(user);
    }

    /**
     * 修改当前用户密码。
     */
    @Transactional
    public void changePassword(ChangePasswordReq req) {
        if (req.getNewPassword() == null || req.getNewPassword().length() < minPasswordLength) {
            throw BizException.badRequest("新密码长度至少 " + minPasswordLength + " 位");
        }
        UserEntity user = userMapper.selectById(UserContext.userId());
        if (user == null || !passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw BizException.badRequest("原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    /**
     * 获取当前登录用户信息。
     */
    public CurrentUserVO me() {
        var u = UserContext.get();
        if (u == null) {
            throw BizException.unauthorized("未登录");
        }
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(u.getUserId());
        vo.setUsername(u.getUsername());
        vo.setRole(u.getRole());
        return vo;
    }
}
