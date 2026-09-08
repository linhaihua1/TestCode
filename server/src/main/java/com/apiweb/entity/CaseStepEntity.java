package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用例步骤（开发文档 §2.1 CaseStep）。
 *
 * <p>从 {@link CaseEntity#steps} JSON 字段拆出来的独立表，支持 10 种步骤类型嵌套，
 * 每个步骤携带 {@code config} JSON，按 stepType 不同而有不同的 schema。
 *
 * <h3>config JSON 范式</h3>
 * <pre>
 * HTTP_REQUEST  → {"method":"POST","url":"...","headers":[...],"query":[...],"body":"...","assertions":[...],"extracts":[...]}
 * SCRIPT        → {"language":"javascript","script":"..."}
 * WAIT          → {"mode":"fixed"/"dynamic","ms":1000}/{"condition":"...","intervalMs":500,"timeoutMs":30000}
 * VARIABLE_ASSIGN → {"name":"x","value":"${y}+1","mode":"literal"/"expression"}
 * IF            → {"condition":"${a}>0","elseBranch":[{"type":"HTTP_REQUEST",...}]}
 * FOR           → {"loopVar":"i","mode":"count"/"list","count":5,"listVar":"ids"}
 * WHILE         → {"condition":"${i}<10","maxIterations":1000,"intervalMs":0}
 * TRANSACTION   → {"transactionName":"TX1","successRule":"all_pass"}
 * ONCE          → {}（无配置）
 * REF_PUBLIC_CASE → {"publicCaseId":"...","lockedVersion":3,"overrides":[{"name":"...","value":"..."}]}
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_case_step")
public class CaseStepEntity extends BaseEntity {
    /** 所属用例 */
    private String caseId;
    /** 父步骤 id（null=顶层）；用于容器嵌套 */
    private String parentId;
    /** 步骤类型枚举值（HTTP_REQUEST / SCRIPT / ...） */
    private String stepType;
    /** 步骤名（用于报告显示） */
    private String name;
    /** 位置：PRE/TEST/POST */
    private String position;
    /** 排序号（同 parentId 内 0-based） */
    private Integer sortOrder;
    /** 是否启用（false=跳过该步骤） */
    private Boolean enabled;
    /** 步骤备注 */
    private String remark;
    /** 步骤配置（JSON，按 stepType 不同 schema） */
    private String config;
    /** 失败策略：stop / continue（默认 stop） */
    private String failStrategy;
}