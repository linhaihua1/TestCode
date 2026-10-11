package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.UserConverter;
import com.apiweb.dto.user.UserCreateReq;
import com.apiweb.dto.user.UserUpdateReq;
import com.apiweb.entity.UserEntity;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.Role;
import com.apiweb.security.UserContext;
import com.apiweb.vo.user.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户业务服务。
 *
 * <p>企业级分层：Controller 只做参数接收 + 调 Service + 返回 VO；
 * 业务规则（角色校验、密码加密、禁止删除自身）全部收敛于此，
 * 事务边界由 {@link Transactional} 明确标注。</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserConverter userConverter;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 密码最小长度（配置项 apiweb.security.min-password-length，默认 6） */
    @Value("${apiweb.security.min-password-length:6}")
    private int minPasswordLength;

    /**
     * 查询全部用户（仅管理员），返回 VO（已剔除密码哈希）。
     */
    public List<UserVO> list() {
        requireAdmin();
        List<UserEntity> users = userMapper.selectList(null);
        return userConverter.toVOList(users);
    }

    /**
     * 新建用户（仅管理员）：BCrypt 加密密码后落库，role 默认 member。
     */
    @Transactional
    public UserVO create(UserCreateReq req) {
        requireAdmin();
        String password = req.getPassword();
        if (password == null || password.length() < minPasswordLength) {
            throw BizException.badRequest("密码至少 " + minPasswordLength + " 位");
        }
        String role = req.getRole() == null ? Role.MEMBER_CODE : req.getRole();
        if (!Role.isValid(role)) {
            throw BizException.badRequest("角色非法: " + role);
        }
        UserEntity user = new UserEntity();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        userMapper.insert(user);
        return userConverter.toVO(user);
    }

    /**
     * 更新用户（仅管理员）：可改 role 或重置密码，未传字段保持原值。
     */
    @Transactional
    public void update(String id, UserUpdateReq req) {
        requireAdmin();
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (req.getRole() != null) {
            if (!Role.isValid(req.getRole())) {
                throw BizException.badRequest("角色非法: " + req.getRole());
            }
            user.setRole(req.getRole());
        }
        if (req.getPassword() != null) {
            if (req.getPassword().length() < minPasswordLength) {
                throw BizException.badRequest("密码至少 " + minPasswordLength + " 位");
            }
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        userMapper.updateById(user);
    }

    /**
     * 删除用户（仅管理员）：禁止删除当前登录用户自身。
     */
    @Transactional
    public void delete(String id) {
        requireAdmin();
        if (id.equals(UserContext.userId())) {
            throw BizException.badRequest("不能删除当前登录用户");
        }
        userMapper.deleteById(id);
    }

    private void requireAdmin() {
        var user = UserContext.get();
        if (user == null || !user.isAdmin()) {
            throw BizException.forbidden("仅管理员可操作");
        }
    }
}
