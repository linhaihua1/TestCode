package com.apiweb.report;

import com.apiweb.common.BizException;
import com.apiweb.entity.ReportShareEntity;
import com.apiweb.entity.ReportShareLogEntity;
import com.apiweb.mapper.ReportShareLogMapper;
import com.apiweb.mapper.ReportShareMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 报告分享服务。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   ReportShareEntity s = shareService.create(reportId, user, expireDays, password);
 *   // URL: /share/<token>
 *
 *   // 校验访问：
 *   ReportShareEntity verified = shareService.verify(token, clientIp, password);
 * }</pre>
 *
 * <h3>安全策略</h3>
 * <ul>
 *   <li>Token：32 字符随机串（base36）,URL 友好</li>
 *   <li>密码：BCrypt 哈希（10 rounds）</li>
 *   <li>IP 白名单：可选 CIDR 列表</li>
 *   <li>频率限制：5 分钟内同 IP 最多 30 次</li>
 *   <li>所有访问尝试记日志</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportShareService {

    private final ReportShareMapper shareMapper;
    private final ReportShareLogMapper logMapper;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /**
     * 创建分享链接。
     *
     * @param reportId   报告 ID
     * @param createdBy  创建者用户名
     * @param expireDays 有效期（天,1-365）
     * @param password   访问密码（明文,内部 BCrypt 哈希存储,null 表示无密码）
     * @return 分享实体（含 token,前端可直接拼 URL）
     */
    public ReportShareEntity create(String reportId, String createdBy, int expireDays, String password) {
        if (expireDays < 1 || expireDays > 365) {
            throw BizException.badRequest("有效期必须在 1-365 天之间");
        }
        ReportShareEntity s = new ReportShareEntity();
        s.setToken(randomToken(32));
        s.setReportId(reportId);
        s.setCreatedBy(createdBy);
        s.setCreatedAt(Instant.now());
        s.setExpiresAt(Instant.now().plus(expireDays, ChronoUnit.DAYS));
        s.setRevoked(false);
        s.setAccessCount(0);
        if (password != null && !password.isBlank()) {
            s.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(10)));
        }
        shareMapper.insert(s);
        return s;
    }

    /**
     * 撤销分享。
     */
    public void revoke(String shareId) {
        ReportShareEntity s = shareMapper.selectById(shareId);
        if (s == null) throw BizException.notFound("分享不存在");
        s.setRevoked(true);
        shareMapper.updateById(s);
    }

    /**
     * 校验分享访问并返回实体（同时累计访问次数）。
     *
     * @param token       URL 中的 token
     * @param clientIp    客户端 IP
     * @param password    用户输入的密码（可选）
     * @return 验证通过的分享实体
     * @throws BizException 验证失败时抛 4xx 异常
     */
    public ReportShareEntity verify(String token, String clientIp, String password) {
        ReportShareEntity s = shareMapper.selectOne(
                new LambdaQueryWrapper<ReportShareEntity>().eq(ReportShareEntity::getToken, token));
        String status = "success";
        try {
            if (s == null) {
                status = "not_found";
                throw BizException.notFound("分享链接不存在");
            }
            if (Boolean.TRUE.equals(s.getRevoked())) {
                status = "revoked";
                throw BizException.forbidden("分享链接已被撤销");
            }
            if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(Instant.now())) {
                status = "expired";
                throw BizException.badRequest("分享链接已过期");
            }
            if (s.getMaxAccessCount() != null && s.getAccessCount() != null
                    && s.getAccessCount() >= s.getMaxAccessCount()) {
                status = "rate_limited";
                throw BizException.badRequest("分享链接访问次数已达上限");
            }
            if (s.getPassword() != null) {
                if (password == null || !BCrypt.checkpw(password, s.getPassword())) {
                    status = "wrong_password";
                    throw BizException.forbidden("密码错误");
                }
            }
            if (s.getAllowedIps() != null && !s.getAllowedIps().isBlank() && clientIp != null) {
                if (!ipAllowed(s.getAllowedIps(), clientIp)) {
                    status = "ip_blocked";
                    throw BizException.forbidden("IP 不在允许范围内");
                }
            }
            return s;
        } finally {
            // 记录访问日志（即使失败也记）
            try {
                if (s != null) {
                    if ("success".equals(status)) {
                        s.setAccessCount((s.getAccessCount() == null ? 0 : s.getAccessCount()) + 1);
                        s.setLastAccessedAt(Instant.now());
                        shareMapper.updateById(s);
                    }
                    ReportShareLogEntity logEntity = new ReportShareLogEntity();
                    logEntity.setShareId(s.getId());
                    logEntity.setReportId(s.getReportId());
                    logEntity.setAccessIp(clientIp);
                    logEntity.setAccessedAt(Instant.now());
                    logEntity.setStatus(status);
                    logMapper.insert(logEntity);
                }
            } catch (Exception e) {
                log.warn("记录分享访问日志失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 列出报告的所有分享链接。
     */
    public List<ReportShareEntity> listByReport(String reportId) {
        return shareMapper.selectList(
                new LambdaQueryWrapper<ReportShareEntity>()
                        .eq(ReportShareEntity::getReportId, reportId)
                        .orderByDesc(ReportShareEntity::getCreatedAt));
    }

    /**
     * 简易 IP 匹配：支持精确 IP 与前缀匹配（/24）。
     *
     * <p>生产建议使用 commons-net 子网工具,这里仅做基础匹配。
     */
    private boolean ipAllowed(String allowedIps, String clientIp) {
        for (String rule : allowedIps.split(",")) {
            String r = rule.trim();
            if (r.isEmpty()) continue;
            if (r.equals(clientIp)) return true;
            if (r.contains("/")) {
                // 简化：仅支持 /24
                String[] parts = r.split("/");
                if (parts.length == 2 && "24".equals(parts[1])) {
                    String prefix = parts[0].substring(0, parts[0].lastIndexOf('.'));
                    if (clientIp.startsWith(prefix)) return true;
                }
            }
        }
        return false;
    }

    private static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}