package com.apiweb.vo.testtask;

import lombok.Data;

/**
 * webhook token 响应 VO。
 */
@Data
public class WebhookTokenVO {

    /** 生成的 token（仅返回一次） */
    private String token;
    /** 完整触发 URL */
    private String url;
}
