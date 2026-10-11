package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.user.UserCreateReq;
import com.apiweb.dto.user.UserUpdateReq;
import com.apiweb.service.UserService;
import com.apiweb.vo.user.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理（仅管理员）。
 *
 * <p>企业级分层：本 Controller 只负责参数接收 + 调 Service + 返回 VO，
 * 不再直接注入 Mapper，业务逻辑全部在 {@link UserService}。</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 查询全部用户列表（仅管理员）。
     */
    @GetMapping
    public Result<List<UserVO>> list() {
        return Result.ok(userService.list());
    }

    /**
     * 新建用户（仅管理员）。
     */
    @AuditLog(action = "create", entityType = "user")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserCreateReq req) {
        return Result.ok(userService.create(req));
    }

    /**
     * 更新用户（仅管理员）。
     */
    @AuditLog(action = "update", entityType = "user")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody UserUpdateReq req) {
        userService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除用户（仅管理员）。
     */
    @AuditLog(action = "delete", entityType = "user")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return Result.ok();
    }
}
