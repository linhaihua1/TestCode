package com.apiweb.vo.module;

import lombok.Data;

import java.time.Instant;

/**
 * 模块 VO（响应，扁平结构，用于 create 返回值等场景）。
 */
@Data
public class ModuleVO {

    /** 模块 ID */
    private String id;
    /** 所属项目 ID */
    private String projectId;
    /** 父节点 ID */
    private String parentId;
    /** 名称 */
    private String name;
    /** case / api / ui / perf */
    private String type;
    /** 排序号 */
    private Integer sortOrder;
    /** 创建时间（UTC） */
    private Instant createdAt;
    /** 更新时间（UTC） */
    private Instant updatedAt;
}
