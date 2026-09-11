package com.apiweb.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解。
 *
 * <p>标注在 Controller 类或方法上，由 {@link RoleAspect} 拦截做角色校验。
 * 权限模型（三角色）：</p>
 * <ul>
 *   <li>{@code admin}：拥有系统所有操作权限（含用户管理、操作日志）</li>
 *   <li>{@code member}：拥有接口自动化、UI 自动化、性能测试、环境配置的全部操作权限</li>
 *   <li>{@code viewer}：只能查看接口自动化与 UI 自动化等页面，禁止一切写操作</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 *   // 1. 仅管理员可访问（类级别）
 *   &#64;RestController
 *   &#64;RequireRole("admin")
 *   &#64;RequestMapping("/api/v1/users")
 *   public class UserController { ... }
 *
 *   // 2. 仅管理员可访问（方法级别，覆盖类级）
 *   &#64;GetMapping
 *   &#64;RequireRole("admin")
 *   public Result&lt;?&gt; list() { ... }
 * </pre>
 *
 * <p><b>viewer 只读</b>由 {@link RoleAspect} 根据 HTTP 方法自动判定：凡 POST/PUT/DELETE/PATCH
 * 请求，viewer 一律 403；无需在每个写接口上重复标注。member 与 admin 的写权限一致。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许访问该接口的最低角色。取值 {@code admin}。
     * 缺省为 {@code ""}，表示不额外限制角色（viewer 只读仍由 HTTP 方法判定）。
     */
    String value() default "";
}
