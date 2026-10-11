package com.apiweb.vo.project;

import lombok.Data;

import java.time.Instant;

/**
 * 项目 VO（响应）。
 */
@Data
public class ProjectVO {

    /** 项目 ID */
    private String id;
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
