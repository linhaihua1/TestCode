package com.apiweb.vo.globalvariable;

import lombok.Data;

import java.time.Instant;

/**
 * 全局变量 VO（响应）。
 *
 * <p>SECRET 类型的 value 已脱敏为掩码 {@code **********}，不向外部泄漏明文。</p>
 */
@Data
public class GlobalVariableVO {

    /** 变量 ID */
    private String id;
    /** 所属项目 ID */
    private String projectId;
    /** 名称 */
    private String name;
    /** 类型 */
    private String type;
    /** 变量值（SECRET 已脱敏） */
    private String value;
    /** 是否加密存储 */
    private Boolean encrypted;
    /** 描述 */
    private String description;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
