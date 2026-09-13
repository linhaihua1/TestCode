package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 全局变量
 *
 * <p>对应表：{@code t_global_variable}。
 * 项目级变量，执行时参与变量合并；encrypted=true 的值以密文存储，引用时自动解密。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_global_variable")
public class GlobalVariableEntity extends BaseEntity {
    /** 所属项目 ID */
    private String projectId;
    /** 名称 */
    private String name;
    /** 类型 */
    private String type;
    /** 变量值 */
    private String value;
    /** 是否加密存储（是则引用时自动解密） */
    private Boolean encrypted;
    /** 描述 */
    private String description;
}
