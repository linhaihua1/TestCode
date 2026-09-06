package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** UI 测试报告 */
@Data
@TableName("t_ui_report")
public class UiReportEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String testCaseId;
    private String name;
    private String status;
    private Integer duration;
    private Instant startedAt;
    /** JSON: 步骤结果 */
    private String details;
    /** MinIO 引用（含截图列表，超 1MB 时） */
    private String detailsRef;
}
