package com.apiweb.security;

import com.apiweb.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthInterceptor} 认证拦截器测试。
 *
 * <p>重点保护「登录态失效」的响应契约：必须返回 HTTP 401 + 统一 {@link Result}
 * 结构（{@code code=1002}），前端据此清理本地登录态并跳转登录页。
 * 若这里退化为手写 JSON、或用错业务码，会导致用户「停在页面上反复报错而不知需重新登录」。
 */
class AuthInterceptorTest {

    private AuthInterceptor interceptor;
    private ObjectMapper objectMapper;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        jwtUtil = mock(JwtUtil.class);
        interceptor = new AuthInterceptor(jwtUtil, objectMapper);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    @Test
    @DisplayName("无 token 时返回 401 + 统一 Result(code=1002)")
    void noTokenReturns401WithUnifiedResult() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean pass = interceptor.preHandle(request, response, null);

        assertFalse(pass, "未登录请求不应放行");
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json;charset=UTF-8");

        JsonNode json = objectMapper.readTree(body.toString());
        assertEquals(ErrorCode.UNAUTHORIZED, json.get("code").asInt(),
                "响应体业务码必须为 1002（UNAUTHORIZED），前端据此跳登录页");
        assertFalse(json.get("message").asText().isBlank(), "应给出用户可读的提示");
    }

    @Test
    @DisplayName("无效 token 同样返回 401 + code=1002")
    void invalidTokenReturns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtUtil.parse("invalid-token")).thenReturn(null);

        boolean pass = interceptor.preHandle(request, response, null);

        assertFalse(pass);
        JsonNode json = objectMapper.readTree(body.toString());
        assertEquals(ErrorCode.UNAUTHORIZED, json.get("code").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(
                json.get("data") == null || json.get("data").isNull(),
                "失败响应 data 应为 null");
    }

    @Test
    @DisplayName("合法 token 放行并写入 UserContext")
    void validTokenPasses() throws Exception {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.get("username", String.class)).thenReturn("admin");
        when(claims.get("role", String.class)).thenReturn("admin");
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtUtil.parse("good-token")).thenReturn(claims);

        boolean pass = interceptor.preHandle(request, response, null);

        org.junit.jupiter.api.Assertions.assertTrue(pass, "合法 token 应放行");
        try {
            CurrentUser user = UserContext.get();
            org.junit.jupiter.api.Assertions.assertNotNull(user);
            assertEquals("user-1", user.getUserId());
            assertEquals("admin", user.getUsername());
            assertEquals("admin", user.getRole());
        } finally {
            UserContext.clear();
        }
    }
}