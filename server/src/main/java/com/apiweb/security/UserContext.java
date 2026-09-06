package com.apiweb.security;

/**
 * 请求级用户上下文（基于 ThreadLocal）。
 *
 * <p>由 {@link AuthInterceptor} 在请求开始时写入，{@code afterCompletion} 中清理。
 * 业务层通过静态方法 {@link #get()} / {@link #userId()} / {@link #username()} 直接获取当前登录用户，
 * 无需在每个 Service 方法签名上传递 userId 参数。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   // Service 层
 *   public void createCase(CaseEntity c) {
 *       c.setCreatorId(UserContext.userId());   // 直接拿当前用户
 *       caseMapper.insert(c);
 *   }
 * </pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>异步线程（如 {@code @Async} / {@code @RabbitListener}）中没有本 ThreadLocal 的值，
 *       需要手动从消息中获取 userId 并注入</li>
 *   <li>定时任务（Quartz Job）也没有，需要从 JobDataMap 取</li>
 *   <li>绝不能在拦截器之前（如过滤器、Listener）中访问 UserContext——那时还没写入</li>
 * </ul>
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    /**
     * 设置当前请求的用户（由 AuthInterceptor 调用）。
     */
    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /**
     * 获取当前用户，未登录时返回 null。
     */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /**
     * 快捷方法：当前用户 ID，未登录返回 null。
     */
    public static String userId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    /**
     * 快捷方法：当前用户名，未登录返回 null。
     */
    public static String username() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUsername();
    }

    /**
     * 清理 ThreadLocal（由 AuthInterceptor.afterCompletion 调用，防止内存泄漏）。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
