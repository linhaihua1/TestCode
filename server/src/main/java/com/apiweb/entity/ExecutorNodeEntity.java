package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 执行机节点（心跳注册资源池）
 *
 * <p>对应表：{@code t_executor_node}。
 * 执行机通过心跳接口上报，超过心跳超时时间未上报会被判定为 offline。</p>
 */
@Data
@TableName("t_executor_node")
public class ExecutorNodeEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 名称 */
    private String name;
    /** JSON: 支持的能力 ["api","ui","perf"] */
    private String capabilities;
    /** online / offline / busy */
    private String status;
    /** 最后一次心跳上报时间 */
    private Instant lastHeartbeat;
    /** 当前正在执行的任务 ID */
    private String currentTask;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
