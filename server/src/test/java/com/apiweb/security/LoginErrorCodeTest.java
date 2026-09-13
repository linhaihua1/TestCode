package com.apiweb.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 错误码契约守卫测试。
 *
 * <p>登录失败与登录态失效是两个语义完全不同的错误：
 * <ul>
 *   <li>{@code 1002 UNAUTHORIZED}：登录态失效（token 过期 / 缺失 / 无效），
 *       前端据此清理本地登录态并跳登录页；</li>
 *   <li>{@code 1006 LOGIN_FAILED}：账号或密码错误，属于业务校验失败，
 *       不能影响登录态，更不能让用户看到「登录已过期」的提示。</li>
 * </ul>
 *
 * <p>两个码值在前后端、数据库审计里都会落地，因此必须严格区分，绝不允许复用。
 */
class LoginErrorCodeTest {

    @Test
    @DisplayName("UNAUTHORIZED 与 LOGIN_FAILED 是不同业务码")
    void distinct() {
        assertNotEquals(
                com.apiweb.common.ErrorCode.UNAUTHORIZED,
                com.apiweb.common.ErrorCode.LOGIN_FAILED,
                "登录态失效与登录失败必须使用不同 code，否则前端会错误清理登录态"
        );
    }

    @Test
    @DisplayName("UNAUTHORIZED = 1002，LOGIN_FAILED = 1006（已发布的契约值）")
    void pinnedCodes() {
        // 这些数字是前后端/审计日志的协议位，改动等于改协议
        assertEquals(1002, com.apiweb.common.ErrorCode.UNAUTHORIZED);
        assertEquals(1006, com.apiweb.common.ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("BizException.loginFailed 与 unauthorized 走不同工厂")
    void factoriesAreDistinct() {
        var u = com.apiweb.common.BizException.unauthorized("x");
        var f = com.apiweb.common.BizException.loginFailed("y");
        assertEquals(com.apiweb.common.ErrorCode.UNAUTHORIZED, u.getCode());
        assertEquals(com.apiweb.common.ErrorCode.LOGIN_FAILED, f.getCode());
    }
}