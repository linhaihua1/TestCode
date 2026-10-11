package com.apiweb.dto.report;

import lombok.Data;

/**
 * 创建报告分享链接的请求体（可选字段）。
 */
@Data
public class ReportShareReq {

    /** 有效期（天），默认 30 */
    private Integer expireDays;

    /** 访问密码（可选，不传则无密码） */
    private String password;
}
