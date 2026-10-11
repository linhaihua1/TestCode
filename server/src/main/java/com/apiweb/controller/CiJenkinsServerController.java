package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.dto.cijenkins.CiJenkinsServerReq;
import com.apiweb.security.RequireRole;
import com.apiweb.security.Role;
import com.apiweb.service.CiJenkinsServerService;
import com.apiweb.service.JenkinsService;
import com.apiweb.vo.cijenkins.CiJenkinsServerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Jenkins 服务器配置管理：维护流水线可调用的 Jenkins 实例，并提供连通性测试与 Job 列表查询。
 *
 * <h3>安全</h3>
 * API Token 落库前用 AES-GCM 加密；列表与详情接口一律不返回该字段。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ci/jenkins-servers")
@RequiredArgsConstructor
@RequireRole(Role.ADMIN_CODE)
public class CiJenkinsServerController {

    private final CiJenkinsServerService jenkinsServerService;
    private final JenkinsService jenkinsService;

    /**
     * 查询 Jenkins 服务器列表（仅未删除，按更新时间倒序；不返回 API Token）。
     */
    @GetMapping
    public Result<List<CiJenkinsServerVO>> list() {
        return Result.ok(jenkinsServerService.list());
    }

    /**
     * 获取 Jenkins 服务器详情（不返回 API Token）。
     */
    @GetMapping("/{id}")
    public Result<CiJenkinsServerVO> get(@PathVariable String id) {
        return Result.ok(jenkinsServerService.get(id));
    }

    /**
     * 新增 Jenkins 服务器配置（API Token 明文入参，落库前加密）。
     *
     * @param req 服务器配置，name / baseUrl 必填
     * @return 已落库的配置（不含 Token）
     */
    @AuditLog(action = "create", entityType = "ci_jenkins_server")
    @PostMapping
    public Result<CiJenkinsServerVO> create(@Valid @RequestBody CiJenkinsServerReq req) {
        return Result.ok(jenkinsServerService.create(req));
    }

    /**
     * 更新 Jenkins 服务器配置（Token 留空表示不修改）。
     *
     * @param id  服务器配置 ID
     * @param req 待更新字段
     * @return 无业务数据
     */
    @AuditLog(action = "update", entityType = "ci_jenkins_server")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody CiJenkinsServerReq req) {
        jenkinsServerService.update(id, req);
        return Result.ok();
    }

    /**
     * 删除 Jenkins 服务器配置（软删除）。
     *
     * @param id 服务器配置 ID
     * @return 无业务数据
     */
    @AuditLog(action = "delete", entityType = "ci_jenkins_server")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        jenkinsServerService.delete(id);
        return Result.ok();
    }

    /**
     * 连通性测试：用当前配置调用 Jenkins 根 API。
     *
     * @param id 服务器配置 ID
     * @return 连通结果（成功时携带 Jenkins 版本号）
     */
    @PostMapping("/{id}/test")
    public Result<JenkinsService.ConnectionResult> test(@PathVariable String id) {
        return Result.ok(jenkinsService.testConnection(jenkinsServerService.getEntity(id)));
    }

    /**
     * 查询该 Jenkins 上的顶层 Job 名称列表（供前端下拉选择）。
     *
     * @param id 服务器配置 ID
     * @return Job 名称列表；连接失败返回空列表
     */
    @GetMapping("/{id}/jobs")
    public Result<List<String>> jobs(@PathVariable String id) {
        return Result.ok(jenkinsService.listJobs(jenkinsServerService.getEntity(id)));
    }
}
