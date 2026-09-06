package com.apiweb.audit;

import com.apiweb.entity.AuditLogEntity;
import com.apiweb.mapper.AuditLogMapper;
import com.apiweb.security.UserContext;
import com.apiweb.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计日志 AOP 切面。
 *
 * <p>拦截所有标注了 {@link AuditLog} 的方法，自动落库到 {@code t_audit_log}。
 *
 * <h3>记录字段</h3>
 * <ul>
 *   <li>userId / username：当前登录用户（从 {@link UserContext} 取）</li>
 *   <li>action：注解的 action + ":error" 后缀（出错时）</li>
 *   <li>entityType：注解的 entityType（被操作的实体类型，如 "case" / "project"）</li>
 *   <li>entityId：若第一个参数是 String，视为实体 id</li>
 *   <li>beforeJson：若第一个参数是对象，序列化为 JSON 作为变更前快照</li>
 *   <li>ip：客户端 IP（来自请求头 X-Forwarded-For 或 socket）</li>
 * </ul>
 *
 * <h3>失败容忍</h3>
 * 审计写入失败不会影响业务（仅 WARN 日志），避免因审计故障导致请求失败。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   &#64;PostMapping
 *   &#64;AuditLog(action = "创建用例", entityType = "case")
 *   public Result&lt;CaseEntity&gt; create(&#64;RequestBody CaseEntity c) { ... }
 * </pre>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    /** 审计日志 Mapper（直接 insert，不走 Service 层以减少依赖） */
    private final AuditLogMapper auditLogMapper;

    /**
     * 环绕通知：执行业务方法，无论成功失败都记录审计。
     *
     * <p>注意：业务方法抛异常时，先记审计（带 ":error" 后缀），再重新抛出，
     * 这样审计能记录到失败的操作（如"删除用例失败"）。</p>
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Object result;
        Throwable error = null;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                record(pjp, auditLog, error);
            } catch (Exception e) {
                // 审计写入失败不能影响业务
                log.warn("审计记录写入失败: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 构造审计记录并写入数据库。
     *
     * <p>约定：
     * <ul>
     *   <li>方法第一个参数若为 String → 视为实体 id（{@code entityId}）</li>
     *   <li>方法第一个参数若为对象 → 序列化为 JSON 作为变更前快照（{@code beforeJson}），
     *       由调用方在 Service 中补充变更后快照（{@code afterJson}）</li>
     * </ul>
     */
    private void record(ProceedingJoinPoint pjp, AuditLog auditLog, Throwable error) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUserId(UserContext.userId());
        entity.setUsername(UserContext.username());
        entity.setAction(auditLog.action() + (error != null ? ":error" : ""));
        entity.setEntityType(auditLog.entityType());

        // 解析参数：第一个 String 视为 id，第一个对象视为变更前快照
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] instanceof String s && !s.isBlank()) {
            entity.setEntityId(s);
        }
        try {
            if (args.length > 0 && !(args[0] instanceof String)) {
                entity.setBeforeJson(JsonUtils.toJson(args[0]));
            }
        } catch (Exception ignored) {
            // 序列化失败忽略（避免审计写入失败）
        }

        // 记录客户端 IP
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            // 优先 X-Forwarded-For（反向代理后），其次 socket 地址
            String xff = req.getHeader("X-Forwarded-For");
            entity.setIp(xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : req.getRemoteAddr());
        }
        auditLogMapper.insert(entity);
    }
}
