package com.apiweb.controller;

import com.apiweb.audit.AuditLog;
import com.apiweb.common.Result;
import com.apiweb.converter.ReportShareConverter;
import com.apiweb.entity.ReportShareEntity;
import com.apiweb.report.ReportShareService;
import com.apiweb.security.UserContext;
import com.apiweb.vo.report.ReportShareVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报告分享：创建/撤销/校验/列表。
 *
 * <h3>公开访问端点</h3>
 * <ul>
 *   <li>{@code GET /api/v1/public/reports/{token}}：无登录校验,通过 token + 可选 password 头访问</li>
 * </ul>
 *
 * <h3>管理端点</h3>
 * <ul>
 *   <li>{@code POST /api/v1/reports/{id}/share}：创建分享（需登录）</li>
 *   <li>{@code POST /api/v1/reports/{id}/share/{shareId}/revoke}：撤销</li>
 *   <li>{@code GET /api/v1/reports/{id}/shares}：列出所有分享</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class ReportShareController {

    private final ReportShareService shareService;
    private final ReportShareConverter shareConverter;

    /**
     * 创建报告分享链接（需登录）。
     *
     * @param id   报告 ID（路径参数，必填）
     * @param body 请求体（可选），含 expireDays 与 password
     * @return 分享记录 VO（含生成的访问 token，不含敏感字段）
     * @audit 写审计日志：action=create_share, entityType=report_share
     */
    @AuditLog(action = "create_share", entityType = "report_share")
    @PostMapping("/api/v1/reports/{id}/share")
    public Result<ReportShareVO> createShare(@PathVariable String id,
                                             @RequestBody(required = false) ShareRequest body) {
        if (body == null) body = new ShareRequest();
        int expireDays = body.expireDays == null ? 30 : body.expireDays;
        return Result.ok(shareConverter.toVO(
                shareService.create(id, UserContext.username(), expireDays, body.password)));
    }

    /**
     * 撤销一条报告分享（使其 token 立即失效）。
     *
     * @param id      报告 ID（路径参数，必填）
     * @param shareId 分享记录 ID（路径参数，必填）
     * @return 无业务数据
     * @audit 写审计日志：action=revoke_share, entityType=report_share
     */
    @AuditLog(action = "revoke_share", entityType = "report_share")
    @PostMapping("/api/v1/reports/{id}/share/{shareId}/revoke")
    public Result<Void> revokeShare(@PathVariable String id, @PathVariable String shareId) {
        shareService.revoke(shareId);
        return Result.ok();
    }

    /**
     * 列出某报告的全部分享记录（含已撤销/过期，由前端区分状态）。
     *
     * @param id 报告 ID（路径参数，必填）
     * @return 分享记录列表（不含敏感字段）
     */
    @GetMapping("/api/v1/reports/{id}/shares")
    public Result<List<ReportShareVO>> listShares(@PathVariable String id) {
        return Result.ok(shareConverter.toVOList(shareService.listByReport(id)));
    }

    /**
     * 公开访问报告分享（免登录，通过 token + 可选 password 头校验，并做 IP 白名单校验）。
     *
     * @param token    分享 token（路径参数，必填）
     * @param password 访问密码（可选，query 参数）
     * @param req      HTTP 请求，用于取客户端 IP
     * @return 校验通过的分享信息（不含敏感字段）
     */
    @GetMapping("/api/v1/public/reports/{token}")
    public Result<ReportShareVO> publicAccess(@PathVariable String token,
                                              @RequestParam(required = false) String password,
                                              HttpServletRequest req) {
        String clientIp = clientIp(req);
        ReportShareEntity s = shareService.verify(token, clientIp, password);
        return Result.ok(shareConverter.toVO(s));
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    @Data
    public static class ShareRequest {
        private Integer expireDays;
        private String password;
    }
}
