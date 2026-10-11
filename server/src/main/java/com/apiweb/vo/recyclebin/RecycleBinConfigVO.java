package com.apiweb.vo.recyclebin;

import lombok.Data;

/**
 * 回收站自动清理配置 VO。
 */
@Data
public class RecycleBinConfigVO {

    private String projectId;
    private Integer cleanupDays;
}
