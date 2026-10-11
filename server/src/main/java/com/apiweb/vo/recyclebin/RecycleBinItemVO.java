package com.apiweb.vo.recyclebin;

import lombok.Data;

import java.time.Instant;

/**
 * 回收站条目统一视图 VO。
 */
@Data
public class RecycleBinItemVO {

    private String id;
    private String type;
    private String name;
    /** MODULE: 子目录+用例数 / API: method+path / CASE: 所属目录 / TASK: 任务类型 */
    private String extra;
    private String deletedBy;
    private Instant deletedAt;
}
