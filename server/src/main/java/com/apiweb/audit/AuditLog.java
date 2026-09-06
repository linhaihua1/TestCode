package com.apiweb.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解：标注在 Controller 方法上，自动记录前后状态。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /** 动作，如 create/update/delete/execute */
    String action();
    /** 实体类型，如 project/case/api */
    String entityType();
}
