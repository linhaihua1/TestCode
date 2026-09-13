package com.apiweb.security;

/**
 * 系统角色枚举（取代散落的 "admin" / "member" / "viewer" 字面量）。
 *
 * <p>角色能力：
 * <ul>
 *   <li>{@link #ADMIN}：全部权限，可访问用户管理 / 审计日志</li>
 *   <li>{@link #MEMBER}：可读写业务数据</li>
 *   <li>{@link #VIEWER}：只读，前端自动禁用写操作按钮</li>
 * </ul>
 *
 * <p>同时提供 {@code *_CODE} 常量，便于在注解（如 {@code @RequireRole}）中使用——
 * 注解属性必须是编译期常量，无法直接引用枚举。
 */
public enum Role {

    /** 管理员 */
    ADMIN("admin"),
    /** 普通成员 */
    MEMBER("member"),
    /** 查看者（只读） */
    VIEWER("viewer");

    /** 管理员角色编码（供注解引用） */
    public static final String ADMIN_CODE = "admin";
    /** 成员角色编码（供注解引用） */
    public static final String MEMBER_CODE = "member";
    /** 查看者角色编码（供注解引用） */
    public static final String VIEWER_CODE = "viewer";

    private final String code;

    Role(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 大小写不敏感解析，无法识别时返回 null。
     */
    public static Role parse(String s) {
        if (s == null || s.isBlank()) return null;
        for (Role r : values()) {
            if (r.code.equalsIgnoreCase(s)) return r;
        }
        return null;
    }

    /**
     * 是否为合法角色编码（用于接口入参校验）。
     */
    public static boolean isValid(String s) {
        return parse(s) != null;
    }
}
