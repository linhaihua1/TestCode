package com.apiweb.security;

/**
 * 请求级用户上下文。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static String userId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String username() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUsername();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
