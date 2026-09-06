package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 调试记录（超 1MB 请求/响应体转存 MinIO，存引用） */
@Data
@TableName("t_debug_record")
public class DebugRecordEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String caseId;
    private String caseNameSnapshot;
    private String environmentId;
    /** local / resource_pool */
    private String executeMode;
    /** pending / running / success / failed / error */
    private String result;
    private Integer totalDuration;
    private String requestSummary;
    private String responseSummary;
    private String assertionResults;
    private String extractedVariables;
    private String stepResults;
    /** MinIO 引用路径（超 1MB 时） */
    private String requestBodyRef;
    private String responseBodyRef;
    private String errorLog;
    private String createdBy;
    private Instant createdAt;
}
