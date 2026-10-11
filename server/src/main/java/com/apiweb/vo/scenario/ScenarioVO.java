package com.apiweb.vo.scenario;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 场景 VO（响应）。
 */
@Data
public class ScenarioVO {

    private String id;
    private String projectId;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    /** 步骤列表（get 接口时填充） */
    private List<ScenarioStepVO> steps;

    @Data
    public static class ScenarioStepVO {
        private String id;
        private String scenarioId;
        private Integer sortOrder;
        private String apiCaseId;
        private String name;
        private String assertions;
        private String extracts;
    }
}
