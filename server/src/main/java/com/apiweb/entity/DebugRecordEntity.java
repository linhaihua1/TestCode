package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 调试记录（超 1MB 请求/响应体转存 MinIO，存引用）
 *
 * <p>对应表：{@code t_debug_record}。
 * 单条用例调试（在用例编辑页点"调试"）产生的记录，与正式报告分开存储。
 * 请求/响应体超过阈值（默认 1MB）时转存对象存储，本表只保留引用。</p>
 */
@Data
@TableName("t_debug_record")
public class DebugRecordEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属用例 ID */
    private String caseId;
    /** 用例名快照（用例改名后历史记录仍可读） */
    private String caseNameSnapshot;
    /** 执行环境 ID */
    private String environmentId;
    /** local / resource_pool */
    private String executeMode;
    /** pending / running / success / failed / error */
    private String result;
    /** 总耗时（毫秒） */
    private Integer totalDuration;
    /** 请求概要（方法/URL/头部摘要） */
    private String requestSummary;
    /** 响应概要（状态码/耗时/大小） */
    private String responseSummary;
    /** 断言结果（JSON 数组） */
    private String assertionResults;
    /** 提取到的变量（JSON 对象） */
    private String extractedVariables;
    /** 步骤执行结果（JSON 数组） */
    private String stepResults;
    /** MinIO 引用路径（超 1MB 时） */
    private String requestBodyRef;
    /** 响应体在对象存储中的引用（报文超阈值时写入） */
    private String responseBodyRef;
    /** 错误日志/堆栈摘要 */
    private String errorLog;
    /** 创建人 */
    private String createdBy;
    /** 创建时间（UTC） */
    private Instant createdAt;
}
