package com.apiweb.security;

import com.apiweb.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.RequestAttributes;

import java.util.Set;

/**
 * 角色权限 AOP 切面。
 *
 * <p>统一落地权限模型，避免在各 Controller 里散落 {@code requireAdmin()} 判断。</p>
 *
 * <h3>权限模型（三角色）</h3>
 * <ul>
 *   <li>{@code admin}：系统所有操作权限（含用户管理、操作日志）</li>
 *   <li>{@code member}：接口自动化、UI 自动化、性能测试、环境配置的全部操作权限</li>
 *   <li>{@code viewer}：只能查看接口自动化与 UI 自动化等页面，禁止一切写操作</li>
 * </ul>
 *
 * <h3>判定规则</h3>
 * <ol>
 *   <li><b>viewer 只读（全局）</b>：对 {@code /api/v1/**} 的 POST/PUT/DELETE/PATCH 请求，
 *       viewer 一律 403。成员与管理员写权限一致。</li>
 *   <li><b>admin 专属</b>：类/方法上标注 {@code @RequireRole("admin")} 的，非 admin 一律 403。</li>
 * </ol>
 *
 * <p><b>豁免</b>：{@code /api/v1/auth/**}（登录、改密）不在此列——改密是每个用户自身的权利，
 * 由 AuthController 内部逻辑保证只能改自己。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RoleAspect {

    /** 写操作的 HTTP 方法（viewer 禁止） */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /**
     * 方法级 @RequireRole（优先级最高，覆盖类级）。
     */
    @Before("@annotation(requireRole)")
    public void checkMethodRole(org.aspectj.lang.JoinPoint jp, RequireRole requireRole) {
        if (!requireRole.value().isEmpty()) {
            requireAdminRole();
        }
    }

    /**
     * 类级 @RequireRole（方法未标注时生效）。
     */
    @Before("@within(requireRole) && !@annotation(com.apiweb.security.RequireRole)")
    public void checkClassRole(org.aspectj.lang.JoinPoint jp, RequireRole requireRole) {
        if (!requireRole.value().isEmpty()) {
            requireAdminRole();
        }
    }

    /**
     * 全局 viewer 只读拦截：所有业务接口的写方法对 viewer 禁止。
     * 豁免：AuthController（登录/改密）、WebhookController（CI 用 token 鉴权）。
     */
    @Before("execution(* com.apiweb.controller..*.*(..)) "
            + "&& !execution(* com.apiweb.controller.AuthController.*(..)) "
            + "&& !execution(* com.apiweb.controller.WebhookController.*(..))")
    public void checkViewerReadOnly(org.aspectj.lang.JoinPoint jp) {
        CurrentUser user = UserContext.get();
        if (user == null || !"viewer".equals(user.getRole())) {
            return;
        }
        if (WRITE_METHODS.contains(currentMethod())) {
            throw BizException.forbidden("查看者无操作权限，仅可查看");
        }
    }

    /**
     * 校验当前用户为 admin。
     */
    private void requireAdminRole() {
        CurrentUser user = UserContext.get();
        if (user == null || !user.isAdmin()) {
            throw BizException.forbidden("仅管理员可操作");
        }
    }

    /**
     * 从当前请求上下文取 HTTP 方法。
     */
    private String currentMethod() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            if (req != null && req.getMethod() != null) {
                return req.getMethod().toUpperCase();
            }
        }
        return "GET";
    }
}
