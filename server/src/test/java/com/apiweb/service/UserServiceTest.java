package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.UserConverter;
import com.apiweb.dto.user.UserCreateReq;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.CurrentUser;
import com.apiweb.security.UserContext;
import com.apiweb.vo.user.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * UserService 业务规则单测：验证密码加密、角色校验、禁止删除自身。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        // 注入 minPasswordLength（@Value 在纯单测中不生效）
        ReflectionTestUtils.setField(userService, "minPasswordLength", 6);
        // 注入真实 UserConverter（纯转换，无副作用，无需 mock）
        ReflectionTestUtils.setField(userService, "userConverter", UserConverter.INSTANCE);
        // 设置 admin 用户上下文，通过 requireAdmin() 校验
        UserContext.set(new CurrentUser("admin-id", "admin", "admin"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void create_shouldEncodePassword() {
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");

        UserCreateReq req = new UserCreateReq();
        req.setUsername("tester");
        req.setPassword("secret1");
        req.setRole("member");

        UserVO vo = userService.create(req);

        assertEquals("tester", vo.getUsername());
        assertEquals("member", vo.getRole());
        verify(passwordEncoder).encode("secret1");
        verify(userMapper).insert(any());
    }

    @Test
    void create_shouldRejectShortPassword() {
        UserCreateReq req = new UserCreateReq();
        req.setUsername("tester");
        req.setPassword("123"); // < 6 位

        assertThrows(BizException.class, () -> userService.create(req));
    }

    @Test
    void create_shouldRejectInvalidRole() {
        UserCreateReq req = new UserCreateReq();
        req.setUsername("tester");
        req.setPassword("secret1");
        req.setRole("superuser"); // 非法角色

        assertThrows(BizException.class, () -> userService.create(req));
    }
}
