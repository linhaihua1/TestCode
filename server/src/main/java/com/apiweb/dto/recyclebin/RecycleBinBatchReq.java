package com.apiweb.dto.recyclebin;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 回收站批量操作请求 DTO（还原 / 永久删除）。
 */
@Data
public class RecycleBinBatchReq {

    /** 对象 ID 列表（必填） */
    @NotEmpty(message = "ids 不能为空")
    private List<String> ids;

    /** 回收对象类型：MODULE / API / CASE / TASK */
    private String type;
}
