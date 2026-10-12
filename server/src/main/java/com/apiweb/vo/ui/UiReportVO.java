package com.apiweb.vo.ui;

import lombok.Data;

import java.time.Instant;

/**
 * UI 测试报告 VO（响应）。
 */
@Data
public class UiReportVO {

    private String id;
    private String projectId;
    private String testCaseId;
    private String name;
    private String status;
    private Integer duration;
    private Instant startedAt;
    /**
     * 步骤执行明细（结构化 JSON 数组）。
     *
     * <p>实体中以 JSON 字符串存储，由 {@code UiConverter#parseReportJsonFields} 在转换后
     * 解析为 List/Map 结构输出，前端可直接按数组使用。</p>
     */
    private Object details;
    /** MinIO 对象引用（明细超阈值时转存） */
    private String detailsRef;
}
