package com.apiweb.entity;

import com.apiweb.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * Jenkins 服务器配置。
 *
 * <p>对应表：{@code t_ci_jenkins_server}。
 *
 * <h3>用途</h3>
 * CI/CD 流水线在 {@code execMode=jenkins} 时，通过本配置调用 Jenkins Remote API
 * 触发远端 Job（buildWithParameters），并轮询构建结果回写流水线执行记录。
 *
 * <h3>安全</h3>
 * {@code apiToken} 为 Jenkins 用户的 API Token，落库前经 {@code AesGcm} 加密；
 * 对外接口永不返回明文（列表/详情接口置空该字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ci_jenkins_server")
public class CiJenkinsServerEntity extends BaseEntity {
    /** Jenkins 显示名（便于区分多套环境） */
    private String name;
    /** Jenkins 根地址，如 http://jenkins.example.com:8080（结尾不带 /） */
    private String baseUrl;
    /** 调用 API 的用户名 */
    private String username;
    /** API Token（AES-GCM 密文，接口出参不返回） */
    private String apiToken;
    /** 认证方式：basic（用户名+Token）/ token（仅 Token） */
    private String authType;
    /** 是否启用 */
    private Boolean enabled;
    /** 备注说明 */
    private String description;
    /** 软删除时间：null 表示未删除 */
    private Instant deletedAt;
}
