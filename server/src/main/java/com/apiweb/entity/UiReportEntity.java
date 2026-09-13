package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * UI 测试报告
 *
 * <p>对应表：{@code t_ui_report}。
 * UI 场景执行报告；步骤截图以 URL 列表形式放在 details 中，明细过大时转存对象存储。</p>
 */
@Data
@TableName("t_ui_report")
public class UiReportEntity {
    /** 主键 ID（UUID） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属项目 ID */
    private String projectId;
    /** 关联的 UI 用例 ID */
    private String testCaseId;
    /** 名称 */
    private String name;
    /** 状态 */
    private String status;
    /** 耗时（毫秒） */
    private Integer duration;
    /** 开始执行时间 */
    private Instant startedAt;
    /** JSON: 步骤结果 */
    private String details;
    /** MinIO 引用（含截图列表，超 1MB 时） */
    private String detailsRef;
}
