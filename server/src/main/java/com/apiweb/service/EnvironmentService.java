package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.EnvironmentConverter;
import com.apiweb.dto.environment.EnvironmentReq;
import com.apiweb.entity.EnvironmentEntity;
import com.apiweb.mapper.EnvironmentMapper;
import com.apiweb.vo.environment.EnvironmentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 环境配置业务服务。
 */
@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final EnvironmentMapper environmentMapper;
    private final EnvironmentConverter environmentConverter;

    /**
     * 查询指定项目的全部环境配置（按创建时间升序）。
     */
    public List<EnvironmentVO> list(String projectId) {
        List<EnvironmentEntity> envs = environmentMapper.selectList(
                new LambdaQueryWrapper<EnvironmentEntity>()
                        .eq(EnvironmentEntity::getProjectId, projectId)
                        .orderByAsc(EnvironmentEntity::getCreatedAt));
        return environmentConverter.toVOList(envs);
    }

    /**
     * 新建环境：补齐 variables/headers 默认空数组后落库。
     */
    @Transactional
    public EnvironmentVO create(EnvironmentReq req) {
        EnvironmentEntity env = new EnvironmentEntity();
        env.setProjectId(req.getProjectId());
        env.setName(req.getName());
        env.setBaseUrl(req.getBaseUrl());
        env.setVariables(req.getVariables() == null ? "[]" : req.getVariables());
        env.setHeaders(req.getHeaders() == null ? "[]" : req.getHeaders());
        environmentMapper.insert(env);
        return environmentConverter.toVO(env);
    }

    /**
     * 更新环境配置（覆盖请求体可写字段）。
     */
    @Transactional
    public void update(String id, EnvironmentReq req) {
        EnvironmentEntity env = environmentMapper.selectById(id);
        if (env == null) {
            throw BizException.notFound("环境不存在");
        }
        env.setProjectId(req.getProjectId());
        env.setName(req.getName());
        env.setBaseUrl(req.getBaseUrl());
        env.setVariables(req.getVariables());
        env.setHeaders(req.getHeaders());
        environmentMapper.updateById(env);
    }

    /**
     * 删除环境（物理删除）。
     */
    @Transactional
    public void delete(String id) {
        environmentMapper.deleteById(id);
    }
}
