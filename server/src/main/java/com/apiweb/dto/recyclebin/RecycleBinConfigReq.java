package com.apiweb.dto.recyclebin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 回收站自动清理配置请求 DTO。
 */
@Data
public class RecycleBinConfigReq {

    /** 项目 ID（必填） */
    @NotBlank(message = "projectId 必填")
    private String projectId;

    /** 自动清理天数；0 = 不自动清理（必填，>= 0） */
    @NotNull(message = "cleanupDays 必填")
    private Integer cleanupDays;
}
