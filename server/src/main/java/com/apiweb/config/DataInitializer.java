package com.apiweb.config;

import com.apiweb.entity.UserEntity;
import com.apiweb.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化：首次启动创建默认管理员 admin / admin@123。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(null);
        if (count == null || count == 0) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            UserEntity admin = new UserEntity();
            admin.setUsername("admin");
            admin.setPasswordHash(encoder.encode("admin@123"));
            admin.setRole("admin");
            userMapper.insert(admin);
            log.info("已创建默认管理员账号: admin / admin@123（请及时修改密码）");
        }
    }
}
