package com.apiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 报告分享链接。
 *
 * <p>用户可针对任意报告生成分享链接,设置有效期、可选密码,便于跨团队传阅。
 *
 * <h3>访问校验</h3>
 * <ol>
 *   <li>{@link #token}：URL 中的随机字符串,主键不暴露</li>
 *   <li>{@link #expiresAt}：过期时间,过期返回 410 Gone</li>
 *   <li>{@link #password}：可选,BCrypt 哈希存储,前端 SHA-256 后传 {@code password} 头</li>
 *   <li>{@link #revoked}：被所有者撤销后拒绝访问</li>
 *   <li>{@link #accessCount} + {@link #lastAccessedAt}：访问计数 + 时间戳</li>
 *   <li>{@link #maxAccessCount}：可选,达到上限后拒绝</li>
 * </ol>
 */
@Data
@TableName("t_report_share")
public class ReportShareEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 分享 token（URL 中实际使用的字符串,32 位随机串） */
    private String token;
    private String reportId;
    /** 创建者 */
    private String createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    /** 可选,BCrypt 哈希;null 表示无密码 */
    private String password;
    /** 被撤销 */
    private Boolean revoked;
    /** 累计访问次数 */
    private Integer accessCount;
    private Instant lastAccessedAt;
    /** 可选,达到上限后禁止继续访问 */
    private Integer maxAccessCount;
    /** 允许查看的客户端 IP 段(CIDR,逗号分隔,留空不限制) */
    private String allowedIps;
}