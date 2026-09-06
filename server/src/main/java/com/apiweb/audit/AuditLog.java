package com.apiweb.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。
 *
 * <p>标注在 Controller 方法上，由 {@link AuditAspect} 拦截后自动写入 t_audit_log 表。
 *
 * <h3>使用示例</h3>
 * <pre>
 *   &#64;PostMapping
 *   &#64;AuditLog(action = "创建用例", entityType = "case")
 *   public Result&lt;CaseEntity&gt; create(&#64;RequestBody CaseEntity entity) {
 *       return Result.ok(caseService.create(entity));
 *   }
 *
 *   &#64;DeleteMapping("/{id}")
 *   &#64;AuditLog(action = "删除用例", entityType = "case")
 *   public Result&lt;Void&gt; delete(&#64;PathVariable String id) {
 *       caseService.delete(id);
 *       return Result.ok();
 *   }
 * </pre>
 *
 * <h3>action 命名约定</h3>
 * 建议使用动词：{@code create / update / delete / execute / approve / reject / import / export}。
 * 失败时 AOP 会自动追加 ":error" 后缀（如 "delete:error"）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /**
     * 动作名称，建议动词（如 create / update / delete）。
     */
    String action();

    /**
     * 实体类型，对应业务表名（如 case / project / api_definition）。
     */
    String entityType();
}
