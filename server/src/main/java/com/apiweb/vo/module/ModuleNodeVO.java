package com.apiweb.vo.module;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块树节点 VO（响应）。
 */
@Data
public class ModuleNodeVO {

    /** 节点 key（=模块 ID） */
    private String key;
    /** 节点标题（=模块名） */
    private String title;
    /** 父节点 ID */
    private String parentId;
    /** case / api / ui / perf */
    private String type;
    /** 排序号 */
    private Integer sortOrder;
    /** 子节点 */
    private List<ModuleNodeVO> children = new ArrayList<>();
}
