package com.apiweb.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器。
 *
 * <p>对所有受保护的接口（除白名单外）做 JWT 校验：
 * <ol>
 *   <li>从请求 Header {@code Authorization: Bearer <token>} 读取 token</li>
 *   <li>用 {@link JwtUtil} 解析签名与有效期</li>
 *   <li>解析成功 → 把当前用户写入 {@link UserContext}（ThreadLocal），放行</li>
 *   <li>解析失败 → 返回 401 + JSON 错误体，阻止请求继续</li>
 * </ol>
 *
 * <p>白名单（如 /api/auth/login）通过 {@code WebConfig} 的 excludePathPatterns 排除本拦截器。
 *
 * <p>线程安全：使用 ThreadLocal 存储 CurrentUser，在 {@code afterCompletion} 中清理，
 * 避免线程复用导致用户身份串台。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** JWT 解析工具（Spring 注入） */
    private final JwtUtil jwtUtil;

    /**
     * 请求前置处理：校验 token，写入 UserContext。
     *
     * @return true=放行；false=已写完 401 响应，框架不再继续执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtUtil.parse(header.substring(7));
            if (claims != null) {
                // 把用户信息放入 ThreadLocal，供 Service 层调用
                UserContext.set(new CurrentUser(
                        claims.getSubject(),
                        claims.get("username", String.class),
                        claims.get("role", String.class)));
                return true;
            }
        }
        // 未带 token / token 无效：返回 401 JSON
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
        return false;
    }

    /**
     * 请求完成（无论成功失败）后清理 ThreadLocal，防止内存泄漏和身份串台。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
