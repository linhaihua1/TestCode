package com.apiweb.vo.auth;

import lombok.Data;

/**
 * 当前用户信息 VO（/me 接口）。
 */
@Data
public class CurrentUserVO {

    private String id;
    private String username;
    private String role;
}
