package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** 测试任务 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_test_task")
public class TestTaskEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;
    /** JSON: string[] */
    private String caseIds;
    private String environmentId;
    /** sequential / parallel */
    private String executeMode;
    private Integer retryCount;
    private Integer timeoutMs;
    private String cronExpr;
    private Boolean enabled;
    private String notifyUrl;
    /** JSON: [{key,value}] */
    private String variables;
    private String baseUrl;
    private String createdBy;
    private Instant deletedAt;
}
