package com.apiweb.security;

import com.apiweb.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录防暴力破解守卫（基于 Redis 失败计数）。
 *
 * <h3>规则</h3>
 * <ol>
 *   <li>同一用户名登录失败时计数 +1（首次计数设置过期时间 = 锁定窗口）</li>
 *   <li>连续失败达到 {@code login-max-failures}（默认 5）次后，锁定
 *       {@code login-lock-minutes}（默认 15）分钟，期间登录一律 1007</li>
 *   <li>登录成功立即清零计数</li>
 * </ol>
 *
 * <p>计数窗口滑动：过期时间从<b>第一次</b>失败开始计，窗口内未再次失败则自动清零；
 * 锁定时间为最后一次达到阈值的时间 + M 分钟（key 过期即解锁，无需后台任务）。</p>
 *
 * <p>Redis 不可用时本守卫降级为放行（不阻塞登录主流程），由日志告警兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginGuardService {

    private final StringRedisTemplate redis;

    /** 连续失败上限（apiweb.security.login-max-failures，默认 5，0=关闭防爆破） */
    @Value("${apiweb.security.login-max-failures:5}")
    private int maxFailures;

    /** 锁定时长（分钟，apiweb.security.login-lock-minutes，默认 15） */
    @Value("${apiweb.security.login-lock-minutes:15}")
    private int lockMinutes;

    /** Redis 计数 key 前缀 */
    @Value("${apiweb.security.login-fail-key-prefix:apiweb:login:fail:}")
    private String keyPrefix;

    /**
     * 登录前检查：用户是否处于锁定状态（计数达到上限才锁定）。锁定则抛 1007（含剩余分钟数）。
     */
    public void checkLocked(String username) {
        if (maxFailures <= 0) {
            return;
        }
        try {
            String key = keyOf(username);
            String count = redis.opsForValue().get(key);
            if (count != null && Long.parseLong(count) >= maxFailures) {
                long seconds = redis.getExpire(key);
                long minutes = Math.max(1, (seconds + 59) / 60);
                log.warn("登录锁定拦截: username={} 剩余约{}分钟", username, minutes);
                throw BizException.loginLocked(
                        "登录失败次数过多，账号已锁定，请约 " + minutes + " 分钟后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("登录锁定检查失败（Redis 不可用?），降级放行: {}", e.getMessage());
        }
    }

    /**
     * 登录失败：计数 +1；达到上限则设置锁定过期时间。
     */
    public void recordFailure(String username) {
        if (maxFailures <= 0) {
            return;
        }
        try {
            String key = keyOf(username);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 首次失败：设置计数窗口（= 锁定窗口），窗口内无失败则自动清零
                redis.expire(key, Duration.ofMinutes(lockMinutes));
            }
            if (count != null && count >= maxFailures) {
                // 达到阈值：重置过期时间为完整锁定窗口（最后一次失败起算）
                redis.expire(key, Duration.ofMinutes(lockMinutes));
                log.warn("账号触发登录锁定: username={} 连续失败{}次", username, count);
            }
        } catch (Exception e) {
            log.error("登录失败计数异常（Redis 不可用?），降级忽略: {}", e.getMessage());
        }
    }

    /**
     * 登录成功：清零计数。
     */
    public void reset(String username) {
        try {
            redis.delete(keyOf(username));
        } catch (Exception e) {
            log.error("登录成功清零异常（Redis 不可用?），降级忽略: {}", e.getMessage());
        }
    }

    private String keyOf(String username) {
        return keyPrefix + username;
    }
}
