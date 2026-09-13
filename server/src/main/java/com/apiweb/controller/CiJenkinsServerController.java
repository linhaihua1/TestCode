package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.entity.CiJenkinsServerEntity;
import com.apiweb.mapper.CiJenkinsServerMapper;
import com.apiweb.security.AesGcm;
import com.apiweb.security.RequireRole;
import com.apiweb.security.Role;
import com.apiweb.service.JenkinsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Jenkins 服务器配置管理：维护流水线可调用的 Jenkins 实例，并提供连通性测试与 Job 列表查询。
 *
 * <h3>为什么单独管理 Jenkins 实例</h3>
 * 一套平台可能对接多套 Jenkins（开发/测试/生产），且凭据需要集中保管，
 * 因此独立为系统级配置（仅管理员维护），流水线只引用其 ID。
 *
 * <h3>安全</h3>
 * API Token 落库前用 AES-GCM 加密；列表与详情接口一律清空该字段，
 * 前端只有「重新填写」才能修改，无法回显旧密钥。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ci/jenkins-servers")
@RequiredArgsConstructor
@RequireRole(Role.ADMIN_CODE)
public class CiJenkinsServerController {

    private final CiJenkinsServerMapper jenkinsServerMapper;
    private final JenkinsService jenkinsService;

    /**
     * 查询 Jenkins 服务器列表（仅未删除，按更新时间倒序；不返回 API Token）。
     *
     * @return Jenkins 服务器列表
     */
    @GetMapping
    public Result<List<CiJenkinsServerEntity>> list() {
        List<CiJenkinsServerEntity> servers = jenkinsServerMapper.selectList(
                new LambdaQueryWrapper<CiJenkinsServerEntity>()
                        .isNull(CiJenkinsServerEntity::getDeletedAt)
                        .orderByDesc(CiJenkinsServerEntity::getUpdatedAt));
        servers.forEach(s -> s.setApiToken(null));
        return Result.ok(servers);
    }

    /**
     * 获取 Jenkins 服务器详情（不返回 API Token）。
     *
     * @param id 服务器配置 ID
     * @return 服务器配置
     */
    @GetMapping("/{id}")
    public Result<CiJenkinsServerEntity> get(@PathVariable String id) {
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(id);
        if (server != null) {
            server.setApiToken(null);
        }
        return Result.ok(server);
    }

    /**
     * 新增 Jenkins 服务器配置（API Token 明文入参，落库前加密）。
     *
     * @param server 服务器配置，name / baseUrl 必填
     * @return 已落库的配置（不含 Token）
     */
    @AuditLog(action = "create", entityType = "ci_jenkins_server")
    @PostMapping
    public Result<CiJenkinsServerEntity> create(@RequestBody CiJenkinsServerEntity server) {
        if (server.getName() == null || server.getName().isBlank()) {
            return Result.fail(1001, "名称必填");
        }
        if (server.getBaseUrl() == null || server.getBaseUrl().isBlank()) {
            return Result.fail(1001, "Jenkins 地址必填");
        }
        encryptToken(server);
        if (server.getEnabled() == null) server.setEnabled(true);
        jenkinsServerMapper.insert(server);
        server.setApiToken(null);
        return Result.ok(server);
    }

    /**
     * 更新 Jenkins 服务器配置。
     * <p>Token 处理：前端留空表示「不修改」，保持原密文；填写新值才覆盖。
     *
     * @param id     服务器配置 ID
     * @param server 待更新字段
     * @return 无业务数据
     */
    @AuditLog(action = "update", entityType = "ci_jenkins_server")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody CiJenkinsServerEntity server) {
        CiJenkinsServerEntity origin = jenkinsServerMapper.selectById(id);
        if (origin == null) {
            return Result.fail(1004, "配置不存在");
        }
        server.setId(id);
        if (server.getApiToken() == null || server.getApiToken().isBlank()) {
            // 前端未填写新 Token → 沿用原密文，避免被空值覆盖导致调用失败
            server.setApiToken(origin.getApiToken());
        } else {
            encryptToken(server);
        }
        if (server.getName() == null) server.setName(origin.getName());
        if (server.getBaseUrl() == null) server.setBaseUrl(origin.getBaseUrl());
        if (server.getEnabled() == null) server.setEnabled(origin.getEnabled());
        jenkinsServerMapper.updateById(server);
        return Result.ok();
    }

    /**
     * 删除 Jenkins 服务器配置（软删除；已引用该服务器的流水线会执行失败并提示配置缺失）。
     *
     * @param id 服务器配置 ID
     * @return 无业务数据
     */
    @AuditLog(action = "delete", entityType = "ci_jenkins_server")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(id);
        if (server != null) {
            server.setDeletedAt(Instant.now());
            jenkinsServerMapper.updateById(server);
        }
        return Result.ok();
    }

    /**
     * 连通性测试：用当前配置调用 Jenkins 根 API，验证地址与凭据。
     *
     * @param id 服务器配置 ID
     * @return 连通结果（成功时携带 Jenkins 版本号）
     */
    @PostMapping("/{id}/test")
    public Result<JenkinsService.ConnectionResult> test(@PathVariable String id) {
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(id);
        if (server == null) {
            return Result.fail(1004, "配置不存在");
        }
        return Result.ok(jenkinsService.testConnection(server));
    }

    /**
     * 查询该 Jenkins 上的顶层 Job 名称列表（供前端下拉选择）。
     *
     * @param id 服务器配置 ID
     * @return Job 名称列表；连接失败返回空列表
     */
    @GetMapping("/{id}/jobs")
    public Result<List<String>> jobs(@PathVariable String id) {
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(id);
        if (server == null) {
            return Result.fail(1004, "配置不存在");
        }
        return Result.ok(jenkinsService.listJobs(server));
    }

    /**
     * API Token 加密存储：已是密文（历史数据）则不重复加密。
     */
    private void encryptToken(CiJenkinsServerEntity server) {
        String token = server.getApiToken();
        if (token == null || token.isBlank()) return;
        server.setApiToken(AesGcm.isCipherText(token) ? token : AesGcm.encrypt(token));
    }
}
