package com.apiweb.security;

import com.apiweb.common.BizException;
import com.apiweb.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录防爆破守卫单测。
 */
@ExtendWith(MockitoExtension.class)
class LoginGuardServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private LoginGuardService guard;

    @BeforeEach
    void setUp() {
        guard = new LoginGuardService(redis);
        ReflectionTestUtils.setField(guard, "maxFailures", 5);
        ReflectionTestUtils.setField(guard, "lockMinutes", 15);
        ReflectionTestUtils.setField(guard, "keyPrefix", "apiweb:login:fail:");
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("未锁定：Redis 无计数 -> 放行")
    void checkLockedShouldPassWhenNoCounter() {
        when(valueOps.get("apiweb:login:fail:alice")).thenReturn(null);
        assertThatCode(() -> guard.checkLocked("alice")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("有失败计数但未达上限 -> 仍放行")
    void checkLockedShouldPassWhenBelowThreshold() {
        when(valueOps.get("apiweb:login:fail:alice")).thenReturn("4");
        assertThatCode(() -> guard.checkLocked("alice")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("已锁定：抛 1007 LOGIN_LOCKED")
    void checkLockedShouldThrow1007WhenLocked() {
        when(valueOps.get("apiweb:login:fail:alice")).thenReturn("5");
        when(redis.getExpire("apiweb:login:fail:alice")).thenReturn(600L);

        assertThatThrownBy(() -> guard.checkLocked("alice"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ErrorCode.LOGIN_LOCKED));
    }

    @Test
    @DisplayName("首次失败：计数 +1 并设置过期窗口")
    void recordFailureShouldIncrementAndExpire() {
        when(valueOps.increment("apiweb:login:fail:alice")).thenReturn(1L);

        guard.recordFailure("alice");

        verify(redis).expire(eq("apiweb:login:fail:alice"), eq(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("达到上限：重置完整锁定窗口")
    void recordFailureShouldResetExpireAtThreshold() {
        when(valueOps.increment("apiweb:login:fail:alice")).thenReturn(5L);

        guard.recordFailure("alice");

        verify(redis).expire(eq("apiweb:login:fail:alice"), eq(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("登录成功：清零计数")
    void resetShouldDeleteKey() {
        guard.reset("alice");
        verify(redis).delete("apiweb:login:fail:alice");
    }

    @Test
    @DisplayName("Redis 不可用：降级放行不抛异常")
    void shouldDegradeWhenRedisDown() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> guard.checkLocked("alice")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("maxFailures=0：关闭防爆破，不访问 Redis")
    void shouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(guard, "maxFailures", 0);

        assertThatCode(() -> guard.checkLocked("alice")).doesNotThrowAnyException();
        verify(valueOps, never()).get(anyString());
    }
}
