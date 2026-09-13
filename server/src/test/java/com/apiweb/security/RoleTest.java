package com.apiweb.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Role} 角色枚举测试。
 *
 * <p>角色编码是 JWT 载荷与权限注解的契约：{@code Role.ADMIN_CODE} 等常量
 * 同时被 {@code @RequireRole} 注解引用，必须与枚举 {@code code} 严格一致。
 */
class RoleTest {

    @Test
    @DisplayName("code 与 *_CODE 常量必须一致（注解依赖这些常量）")
    void codeConstantsMatchEnumCodes() {
        assertEquals(Role.ADMIN.getCode(), Role.ADMIN_CODE);
        assertEquals(Role.MEMBER.getCode(), Role.MEMBER_CODE);
        assertEquals(Role.VIEWER.getCode(), Role.VIEWER_CODE);
    }

    @Test
    @DisplayName("编码与前端 constants 的 ROLES 契约一致")
    void codesMatchFrontendContract() {
        assertEquals("admin", Role.ADMIN.getCode());
        assertEquals("member", Role.MEMBER.getCode());
        assertEquals("viewer", Role.VIEWER.getCode());
    }

    @Test
    @DisplayName("parse: 大小写不敏感，无法识别返回 null")
    void parseIsCaseInsensitive() {
        assertSame(Role.ADMIN, Role.parse("admin"));
        assertSame(Role.ADMIN, Role.parse("ADMIN"));
        assertSame(Role.MEMBER, Role.parse("Member"));
        assertSame(Role.VIEWER, Role.parse("VIEWER"));
        assertNull(Role.parse("root"));
        assertNull(Role.parse(null));
        assertNull(Role.parse(""));
    }

    @Test
    @DisplayName("isValid: 用于接口入参校验")
    void isValidRejectsUnknownRole() {
        assertTrue(Role.isValid("admin"));
        assertTrue(Role.isValid("member"));
        assertTrue(Role.isValid("viewer"));
        assertFalse(Role.isValid("superuser"));
        assertFalse(Role.isValid(null));
        assertFalse(Role.isValid(""));
    }
}
