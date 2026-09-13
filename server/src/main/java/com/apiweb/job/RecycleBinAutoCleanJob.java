package com.apiweb.job;

import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.ModuleEntity;
import com.apiweb.entity.RecycleBinConfigEntity;
import com.apiweb.entity.TestTaskEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.ModuleMapper;
import com.apiweb.mapper.RecycleBinConfigMapper;
import com.apiweb.mapper.TestTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 回收站自动清理 Job（开发文档 §7.8）。
 *
 * <p>每日凌晨由 Quartz 调度触发：
 * <ol>
 *   <li>遍历每个项目的回收站配置</li>
 *   <li>{@code cleanupDays == 0} 表示不自动清理，跳过</li>
 *   <li>否则物理删除该项目所有超过 N 天的回收站条目（四类：模块/接口/用例/任务）</li>
 * </ol>
 *
 * <p>集群安全：{@link DisallowConcurrentExecution} 保证同一时间只有一个节点执行。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class RecycleBinAutoCleanJob implements Job {

    private final RecycleBinConfigMapper configMapper;
    private final ModuleMapper moduleMapper;
    private final ApiDefinitionMapper apiMapper;
    private final CaseMapper caseMapper;
    private final TestTaskMapper testTaskMapper;

    /**
     * Quartz 定时入口：每日凌晨触发，按各项目回收站配置清理过期软删除条目。
     *
     * <p>逻辑：
     * <ul>
     *   <li>读取全部项目的回收站配置；{@code cleanupDays == null || <= 0} 表示该项目不自动清理，跳过；</li>
     *   <li>以 {@code now - cleanupDays*86400s} 为阈值，物理删除 deleted_at 早于该时间的条目；</li>
     *   <li>四类资源（模块/接口/用例/任务）均按 projectId 过滤后逐条 deleteById。</li>
     * </ul>
     *
     * <p>该 Job 标注 {@link DisallowConcurrentExecution}，集群中同一时刻仅一个节点执行，
     * 避免重复删除。</p>
     *
     * @param context Quartz 作业执行上下文
     */
    @Override
    public void execute(JobExecutionContext context) {
        List<RecycleBinConfigEntity> configs = configMapper.selectList(null);
        if (configs.isEmpty()) {
            log.debug("[RecycleBinAutoClean] 无项目配置回收站清理，跳过");
            return;
        }
        int totalCleaned = 0;
        for (RecycleBinConfigEntity cfg : configs) {
            if (cfg.getCleanupDays() == null || cfg.getCleanupDays() <= 0) {
                continue;
            }
            Instant threshold = Instant.now().minusSeconds(cfg.getCleanupDays() * 86400L);
            int cleaned = cleanProject(cfg.getProjectId(), threshold);
            totalCleaned += cleaned;
            log.info("[RecycleBinAutoClean] 项目 {} 清理 {} 项（>{} 天）",
                    cfg.getProjectId(), cleaned, cfg.getCleanupDays());
        }
        log.info("[RecycleBinAutoClean] 全部项目共清理 {} 项", totalCleaned);
    }

    /**
     * 清理单个项目下、软删除时间早于阈值的四类资源（模块 / 接口 / 用例 / 任务）。
     *
     * <p>均为不可逆的<b>物理删除</b>（deleteById）。软删除标记 deleted_at 由业务层在
     * 用户移入回收站时写入，本方法据此判断条目的「过期」。返回本项目本次清理的条目数。</p>
     *
     * @param projectId 项目 ID
     * @param threshold 软删除时间阈值（早于该时间的条目被清理）
     * @return 本次清理的条目总数
     */
    private int cleanProject(String projectId, Instant threshold) {
        int n = 0;
        // 模块
        List<ModuleEntity> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<ModuleEntity>()
                        .eq(ModuleEntity::getProjectId, projectId)
                        .lt(ModuleEntity::getDeletedAt, threshold));
        for (ModuleEntity m : modules) {
            moduleMapper.deleteById(m.getId());
            n++;
        }
        // 接口
        List<ApiDefinitionEntity> apis = apiMapper.selectList(
                new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getProjectId, projectId)
                        .lt(ApiDefinitionEntity::getDeletedAt, threshold));
        for (ApiDefinitionEntity a : apis) {
            apiMapper.deleteById(a.getId());
            n++;
        }
        // 用例
        List<CaseEntity> cases = caseMapper.selectList(
                new LambdaQueryWrapper<CaseEntity>()
                        .eq(CaseEntity::getProjectId, projectId)
                        .lt(CaseEntity::getDeletedAt, threshold));
        for (CaseEntity c : cases) {
            caseMapper.deleteById(c.getId());
            n++;
        }
        // 任务
        List<TestTaskEntity> tasks = testTaskMapper.selectList(
                new LambdaQueryWrapper<TestTaskEntity>()
                        .eq(TestTaskEntity::getProjectId, projectId)
                        .lt(TestTaskEntity::getDeletedAt, threshold));
        for (TestTaskEntity t : tasks) {
            testTaskMapper.deleteById(t.getId());
            n++;
        }
        return n;
    }
}