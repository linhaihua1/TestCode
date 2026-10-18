package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.dto.auth.AuthRequest;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.JwtUtil;
import com.apiweb.security.LoginGuardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService 业务规则单测。
 *
 * <p>覆盖自助注册开关（{@code apiweb.security.allow-register}）：关闭后必须拒绝注册，
 * 且不触碰数据库；开启时正常建号（密码加密存储）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private LoginGuardService loginGuard;

    @InjectMocks
    private AuthService authService;

    private AuthRequest req(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    @Test
    void register_shouldRejectWhenSelfRegistrationDisabled() {
        ReflectionTestUtils.setField(authService, "minPasswordLength", 6);
        ReflectionTestUtils.setField(authService, "allowRegister", false);

        BizException ex = assertThrows(BizException.class,
                () -> authService.register(req("hacker", "secret1")));

        assertTrue(ex.getMessage().contains("自助注册"));
        // 关闭注册时必须直接拒绝，不得读库/写库
        verifyNoInteractions(userMapper);
    }

    @Test
    void register_shouldCreateUserWhenEnabled() {
        ReflectionTestUtils.setField(authService, "minPasswordLength", 6);
        ReflectionTestUtils.setField(authService, "allowRegister", true);
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(userMapper.selectCount(any())).thenReturn(0L);

        authService.register(req("tester", "secret1"));

        verify(passwordEncoder).encode("secret1");
        verify(userMapper).insert(any());
    }

    @Test
    void register_shouldRejectShortPassword() {
        ReflectionTestUtils.setField(authService, "minPasswordLength", 6);
        ReflectionTestUtils.setField(authService, "allowRegister", true);

        assertThrows(BizException.class, () -> authService.register(req("tester", "123")));
        verifyNoInteractions(userMapper);
    }
}
