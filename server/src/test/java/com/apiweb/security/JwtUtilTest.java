package com.apiweb.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link JwtUtil} 测试。
 *
 * <p>重点验证签发/解析闭环，以及无效 token 的兜底（返回 null 而非抛异常，
 * 这是 {@link AuthInterceptor} 依赖的关键契约）。
 */
class JwtUtilTest {

    /** 固定 32 字节以上的密钥，保证测试可重复 */
    private static final String SECRET = "test-secret-key-32bytes-minimum!!";

    @Test
    @DisplayName("签发后可解析出相同 subject/username/role")
    void generateAndParseRoundTrip() {
        JwtUtil util = new JwtUtil(SECRET, 24);
        String token = util.generateToken("user-1", "admin", "admin");

        Claims claims = util.parse(token);
        assertNotNull(claims, "合法 token 应能解析");
        assertEquals("user-1", claims.getSubject());
        assertEquals("admin", claims.get("username", String.class));
        assertEquals("admin", claims.get("role", String.class));
    }

    @Test
    @DisplayName("无效 token 返回 null（不抛异常）")
    void parseInvalidTokenReturnsNull() {
        JwtUtil util = new JwtUtil(SECRET, 24);
        assertNull(util.parse("invalid-token"));
        assertNull(util.parse(""));
    }

    @Test
    @DisplayName("错误密钥解析失败返回 null")
    void parseWithWrongSecretReturnsNull() {
        JwtUtil signer = new JwtUtil(SECRET, 24);
        String token = signer.generateToken("u", "n", "admin");

        JwtUtil wrongKey = new JwtUtil("another-secret-key-32bytes-abcdefgh!!", 24);
        assertNull(wrongKey.parse(token), "用错误密钥应解析失败");
    }

    @Test
    @DisplayName("空密钥自动生成随机密钥，签发解析仍闭环")
    void blankSecretAutoGeneratesKey() {
        JwtUtil util = new JwtUtil("", 1);
        String token = util.generateToken("u", "n", "member");
        Claims claims = util.parse(token);
        assertNotNull(claims);
        assertEquals("u", claims.getSubject());
    }
}
