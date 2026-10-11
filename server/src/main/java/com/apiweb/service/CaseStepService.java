package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.CaseStepConverter;
import com.apiweb.dto.casestep.CaseStepReq;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.mapper.CaseStepMapper;
import com.apiweb.vo.casestep.CaseStepVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 用例步骤业务服务。
 */
@Service
@RequiredArgsConstructor
public class CaseStepService {

    private final CaseStepMapper caseStepMapper;
    private final CaseStepConverter caseStepConverter;

    /**
     * 列出某用例的全部步骤（扁平列表，按 position 与 sortOrder 升序）。
     */
    public List<CaseStepVO> list(String caseId) {
        return caseStepConverter.toVOList(caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>()
                        .eq(CaseStepEntity::getCaseId, caseId)
                        .orderByAsc(CaseStepEntity::getPosition, CaseStepEntity::getSortOrder)));
    }

    /**
     * 新增单个步骤：补齐默认值后落库。
     */
    @Transactional
    public CaseStepVO create(CaseStepReq req) {
        CaseStepEntity step = caseStepConverter.toEntity(req);
        if (step.getSortOrder() == null) step.setSortOrder(0);
        if (step.getPosition() == null || step.getPosition().isBlank()) step.setPosition("TEST");
        if (step.getEnabled() == null) step.setEnabled(true);
        if (step.getFailStrategy() == null || step.getFailStrategy().isBlank()) step.setFailStrategy("stop");
        if (step.getConfig() == null || step.getConfig().isBlank()) step.setConfig("{}");
        caseStepMapper.insert(step);
        return caseStepConverter.toVO(step);
    }

    /**
     * 更新单个步骤。
     */
    @Transactional
    public void update(String id, CaseStepReq req) {
        if (caseStepMapper.selectById(id) == null) {
            throw BizException.notFound("步骤不存在");
        }
        CaseStepEntity step = caseStepConverter.toEntity(req);
        step.setId(id);
        caseStepMapper.updateById(step);
    }

    /**
     * 删除步骤，并级联删除其所有子步骤。
     */
    @Transactional
    public void delete(String id) {
        List<String> ids = new ArrayList<>();
        collectSubtree(id, ids);
        ids.forEach(caseStepMapper::deleteById);
    }

    /**
     * 批量保存（前端拖拽后整体提交：清空旧步骤 + 重插新步骤含父子关系）。
     */
    @Transactional
    public void saveBatch(String caseId, List<CaseStepReq> steps) {
        if (caseId == null || caseId.isBlank()) {
            throw BizException.badRequest("caseId 必填");
        }
        caseStepMapper.delete(
                new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getCaseId, caseId));
        if (steps != null) {
            for (CaseStepReq r : steps) {
                CaseStepEntity s = caseStepConverter.toEntity(r);
                s.setId(null);
                s.setCaseId(caseId);
                if (s.getConfig() == null || s.getConfig().isBlank()) s.setConfig("{}");
                if (s.getFailStrategy() == null || s.getFailStrategy().isBlank()) s.setFailStrategy("stop");
                if (s.getEnabled() == null) s.setEnabled(true);
                if (s.getPosition() == null || s.getPosition().isBlank()) s.setPosition("TEST");
                caseStepMapper.insert(s);
            }
        }
    }

    /**
     * 调整步骤顺序：按传入 stepIds 顺序重置 sortOrder。
     */
    @Transactional
    public void reorder(List<String> stepIds) {
        if (stepIds == null || stepIds.isEmpty()) {
            throw BizException.badRequest("stepIds 必填");
        }
        for (int i = 0; i < stepIds.size(); i++) {
            CaseStepEntity s = new CaseStepEntity();
            s.setId(stepIds.get(i));
            s.setSortOrder(i);
            caseStepMapper.updateById(s);
        }
    }

    private void collectSubtree(String rootId, List<String> acc) {
        acc.add(rootId);
        List<CaseStepEntity> children = caseStepMapper.selectList(
                new LambdaQueryWrapper<CaseStepEntity>().eq(CaseStepEntity::getParentId, rootId));
        for (CaseStepEntity c : children) {
            collectSubtree(c.getId(), acc);
        }
    }
}
