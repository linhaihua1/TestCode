package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * UI 测试用例（前置/测试/后置三段式步骤）
 *
 * <p>对应表：{@code t_ui_test_case}。
 * UI 用例由 setup（前置）/ steps（测试）/ teardown（后置）三段步骤组成。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ui_test_case")
public class UiTestCaseEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 环境 baseUrl */
    private String baseUrl;
    /** JSON: 浏览器操作步骤数组 */
    /** 前置步骤（JSON 数组） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String setupSteps;
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String steps;
    /** 后置步骤（JSON 数组） */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String teardownSteps;
}
