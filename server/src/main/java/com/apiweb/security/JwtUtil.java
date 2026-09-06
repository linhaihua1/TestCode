package com.apiweb.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类。
 *
 * <p>封装 JWT 的签发与解析，所有逻辑集中在 {@link AuthController}（签发）和 {@link AuthInterceptor}（解析）。
 *
 * <h3>Token 结构</h3>
 * <pre>
 * Header.Payload.Signature
 *   ├── alg: HS256
 *   ├── typ: JWT
 * ├── sub: userId   (String，UUID)
 * ├── username: 用户名
 * ├── role: 角色（admin/manager/engineer/viewer）
 * ├── iat: 签发时间（Unix ms）
 * └── exp: 过期时间（Unix ms）
 * </pre>
 *
 * <h3>密钥要求</h3>
 * HS256 算法要求密钥 ≥ 256 bit（32 字节）。{@link Keys#hmacShaKeyFor(byte[])} 会在密钥长度不足时抛异常。
 * 生产环境务必通过环境变量 {@code APIWEB_JWT_SECRET} 传入强随机密钥（如 {@code openssl rand -base64 48}）。
 */
@Component
public class JwtUtil {

    /** 签名密钥（HMAC-SHA256） */
    private final SecretKey key;
    /** token 有效期（毫秒） */
    private final long expireMillis;

    /**
     * Spring 注入密钥与有效期。
     *
     * @param secret      签名密钥（至少 32 字节）
     * @param expireHours 过期小时数（默认 24h，配置在 application.yml）
     */
    public JwtUtil(@Value("${apiweb.jwt.secret}") String secret,
                   @Value("${apiweb.jwt.expire-hours:24}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    /**
     * 签发 token。
     *
     * @param userId   用户 ID（写入 sub 字段）
     * @param username 用户名（写入 username claim）
     * @param role     角色（写入 role claim，可选）
     * @return 紧凑型 JWT 字符串
     */
    public String generateToken(String userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验证 token。
     *
     * @param token 待解析的 JWT 字符串
     * @return Claims 对象（包含 sub / username / role / iat / exp）
     *         解析失败（签名错 / 过期 / 格式错）返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
