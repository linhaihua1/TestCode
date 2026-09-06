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
 * 审计切面：拦截 @AuditLog 注解的方法，记录操作人、IP、前后状态。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogMapper auditLogMapper;

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
                log.warn("审计记录写入失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private void record(ProceedingJoinPoint pjp, AuditLog auditLog, Throwable error) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUserId(UserContext.userId());
        entity.setUsername(UserContext.username());
        entity.setAction(auditLog.action() + (error != null ? ":error" : ""));
        entity.setEntityType(auditLog.entityType());

        // 第一个参数若是字符串视为实体 id（Controller 惯例）
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] instanceof String s && !s.isBlank()) {
            entity.setEntityId(s);
        }
        try {
            if (args.length > 0 && !(args[0] instanceof String)) {
                entity.setBeforeJson(JsonUtils.toJson(args[0]));
            }
        } catch (Exception ignored) {
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            entity.setIp(req.getRemoteAddr());
        }
        auditLogMapper.insert(entity);
    }
}
