package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 测试任务执行记录
 *
 * <p>对应表：{@code t_test_task_run}。
 * 任务的一次执行实例（run），明细较大时转存对象存储，本表只留 detailsRef。</p>
 */
@Data
@TableName("t_test_task_run")
public class TestTaskRunEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 关联任务 ID（手动执行时可为空） */
    private String taskId;
    /** pending / running / success / failed / error */
    private String result;
    /** 耗时（毫秒） */
    private Integer duration;
    /** JSON: 明细 */
    private String details;
    /** MinIO 引用（明细超 1MB 时） */
    private String detailsRef;
    /** 开始执行时间 */
    private Instant startedAt;
    /** 执行结束时间 */
    private Instant endedAt;
}
