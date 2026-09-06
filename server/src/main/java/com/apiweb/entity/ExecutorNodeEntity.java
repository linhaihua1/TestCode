package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 执行机节点（心跳注册资源池） */
@Data
@TableName("t_executor_node")
public class ExecutorNodeEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    /** JSON: 支持的能力 ["api","ui","perf"] */
    private String capabilities;
    /** online / offline / busy */
    private String status;
    private Instant lastHeartbeat;
    private String currentTask;
    private Instant createdAt;
    private Instant updatedAt;
}
