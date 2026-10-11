package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.CiJenkinsServerConverter;
import com.apiweb.dto.cijenkins.CiJenkinsServerReq;
import com.apiweb.entity.CiJenkinsServerEntity;
import com.apiweb.mapper.CiJenkinsServerMapper;
import com.apiweb.security.AesGcm;
import com.apiweb.vo.cijenkins.CiJenkinsServerVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Jenkins 服务器配置业务服务。
 */
@Service
@RequiredArgsConstructor
public class CiJenkinsServerService {

    private final CiJenkinsServerMapper jenkinsServerMapper;
    private final CiJenkinsServerConverter jenkinsServerConverter;

    /**
     * 查询 Jenkins 服务器列表（仅未删除，按更新时间倒序）。
     */
    public List<CiJenkinsServerVO> list() {
        return jenkinsServerConverter.toVOList(jenkinsServerMapper.selectList(
                new LambdaQueryWrapper<CiJenkinsServerEntity>()
                        .isNull(CiJenkinsServerEntity::getDeletedAt)
                        .orderByDesc(CiJenkinsServerEntity::getUpdatedAt)));
    }

    /**
     * 获取 Jenkins 服务器详情。
     */
    public CiJenkinsServerVO get(String id) {
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(id);
        if (server == null) {
            throw BizException.notFound("配置不存在");
        }
        return jenkinsServerConverter.toVO(server);
    }

    /**
     * 新增 Jenkins 服务器配置（API Token 落库前加密）。
     */
    @Transactional
    public CiJenkinsServerVO create(CiJenkinsServerReq req) {
        CiJenkinsServerEntity server = new CiJenkinsServerEntity();
        server.setName(req.getName());
        server.setBaseUrl(req.getBaseUrl());
        server.setUsername(req.getUsername());
        server.setApiToken(encryptToken(req.getApiToken()));
        server.setAuthType(req.getAuthType());
        server.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        server.setDescription(req.getDescription());
        jenkinsServerMapper.insert(server);
        return jenkinsServerConverter.toVO(server);
    }

    /**
     * 更新 Jenkins 服务器配置（Token 留空表示不修改）。
     */
    @Transactional
    public void update(String id, CiJenkinsServerReq req) {
        CiJenkinsServerEntity origin = jenkinsServerMapper.selectById(id);
        if (origin == null) {
            throw BizException.notFound("配置不存在");
        }
        CiJenkinsServerEntity server = new CiJenkinsServerEntity();
        server.setId(id);
        if (req.getApiToken() == null || req.getApiToken().isBlank()) {
            server.setApiToken(origin.getApiToken());
        } else {
            server.setApiToken(encryptToken(req.getApiToken()));
        }
        server.setName(req.getName() == null ? origin.getName() : req.getName());
        server.setBaseUrl(req.getBaseUrl() == null ? origin.getBaseUrl() : req.getBaseUrl());
        server.setUsername(req.getUsername() == null ? origin.getUsername() : req.getUsername());
        server.setAuthType(req.getAuthType() == null ? origin.getAuthType() : req.getAuthType());
        server.setEnabled(req.getEnabled() == null ? origin.getEnabled() : req.getEnabled());
        server.setDescription(req.getDescription() == null ? origin.getDescription() : req.getDescription());
        jenkinsServerMapper.updateById(server);
    }

    /**
     * 删除 Jenkins 服务器配置（软删除）。
     */
    @Transactional
    public void delete(String id) {
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(id);
        if (server != null) {
            server.setDeletedAt(Instant.now());
            jenkinsServerMapper.updateById(server);
        }
    }

    /**
     * 供连通性测试 / Job 列表查询使用：获取实体（含解密后的 Token）。
     */
    public CiJenkinsServerEntity getEntity(String id) {
        CiJenkinsServerEntity server = jenkinsServerMapper.selectById(id);
        if (server == null) {
            throw BizException.notFound("配置不存在");
        }
        return server;
    }

    private String encryptToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return AesGcm.isCipherText(token) ? token : AesGcm.encrypt(token);
    }
}
