package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.ProjectConverter;
import com.apiweb.dto.project.ProjectReq;
import com.apiweb.entity.ProjectEntity;
import com.apiweb.mapper.ProjectMapper;
import com.apiweb.vo.project.ProjectVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目业务服务。
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectConverter projectConverter;

    /**
     * 查询项目列表（可按名称关键字模糊搜索，按更新时间倒序）。
     */
    public List<ProjectVO> list(String keyword) {
        List<ProjectEntity> projects = projectMapper.selectList(
                new LambdaQueryWrapper<ProjectEntity>()
                        .like(keyword != null && !keyword.isBlank(),
                                ProjectEntity::getName, keyword)
                        .orderByDesc(ProjectEntity::getUpdatedAt));
        return projectConverter.toVOList(projects);
    }

    /**
     * 获取项目详情。
     */
    public ProjectVO get(String id) {
        ProjectEntity project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        return projectConverter.toVO(project);
    }

    /**
     * 新建项目。
     */
    @Transactional
    public ProjectVO create(ProjectReq req) {
        ProjectEntity project = new ProjectEntity();
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        projectMapper.insert(project);
        return projectConverter.toVO(project);
    }

    /**
     * 更新项目（覆盖请求体可写字段）。
     */
    @Transactional
    public void update(String id, ProjectReq req) {
        ProjectEntity project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        projectMapper.updateById(project);
    }

    /**
     * 删除项目（物理删除，调用方需确保已清理其下用例/接口等数据）。
     */
    @Transactional
    public void delete(String id) {
        projectMapper.deleteById(id);
    }
}
