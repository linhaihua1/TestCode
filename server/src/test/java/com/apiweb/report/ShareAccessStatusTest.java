package com.apiweb.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShareAccessStatus} 分享访问结果枚举测试。
 *
 * <p>编码会写入 {@code t_report_share_log.status} 作为访问审计，
 * 属于持久化契约，不允许随意改动。
 */
class ShareAccessStatusTest {

    @Test
    @DisplayName("编码与分享访问日志契约一致")
    void codes() {
        assertEquals("success", ShareAccessStatus.SUCCESS.getCode());
        assertEquals("not_found", ShareAccessStatus.NOT_FOUND.getCode());
        assertEquals("revoked", ShareAccessStatus.REVOKED.getCode());
        assertEquals("expired", ShareAccessStatus.EXPIRED.getCode());
        assertEquals("rate_limited", ShareAccessStatus.RATE_LIMITED.getCode());
        assertEquals("wrong_password", ShareAccessStatus.WRONG_PASSWORD.getCode());
        assertEquals("ip_blocked", ShareAccessStatus.IP_BLOCKED.getCode());
    }

    @Test
    @DisplayName("编码唯一且非空")
    void codesAreUniqueAndNotBlank() {
        var codes = Arrays.stream(ShareAccessStatus.values())
                .map(ShareAccessStatus::getCode)
                .toList();
        assertEquals(codes.size(), new HashSet<>(codes).size(), "存在重复 code: " + codes);
        codes.forEach(c -> assertNotNull(c));
        codes.forEach(c -> assertTrue(!c.isBlank(), "code 不应为空白"));
    }
}
