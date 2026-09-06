package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 测试任务执行记录 */
@Data
@TableName("t_test_task_run")
public class TestTaskRunEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    /** pending / running / success / failed / error */
    private String result;
    private Integer duration;
    /** JSON: 明细 */
    private String details;
    /** MinIO 引用（明细超 1MB 时） */
    private String detailsRef;
    private Instant startedAt;
    private Instant endedAt;
}
