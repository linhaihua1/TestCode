package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 接口测试报告 */
@Data
@TableName("t_report")
public class ReportEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String scenarioId;
    private String name;
    private String status;
    private Integer duration;
    private Instant startedAt;
}
