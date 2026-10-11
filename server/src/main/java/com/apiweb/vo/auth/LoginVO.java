package com.apiweb.vo.auth;

import lombok.Data;

/**
 * 登录响应 VO：token + 用户基本信息。
 */
@Data
public class LoginVO {

    /** JWT token */
    private String token;

    /** 用户信息 */
    private UserBrief user;

    @Data
    public static class UserBrief {
        private String id;
        private String username;
        private String role;
    }
}
