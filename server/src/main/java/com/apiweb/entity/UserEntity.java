package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 系统用户 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class UserEntity extends BaseEntity {
    private String username;
    private String passwordHash;
    /** admin / member / viewer */
    private String role;
}
