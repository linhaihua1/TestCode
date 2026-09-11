package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.apiweb.util.JsonStringDeserializer;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** UI 测试用例（前置/测试/后置三段式步骤） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ui_test_case")
public class UiTestCaseEntity extends BaseEntity {
    private String projectId;
    private String name;
    private String description;
    private String baseUrl;
    /** JSON: 浏览器操作步骤数组 */
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String setupSteps;
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String steps;
    @JsonDeserialize(using = JsonStringDeserializer.class)
    private String teardownSteps;
}
