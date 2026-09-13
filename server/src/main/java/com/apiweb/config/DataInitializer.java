package com.apiweb.config;

import com.apiweb.entity.UserEntity;
import com.apiweb.mapper.UserMapper;
import com.apiweb.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化：库中无任何用户时，按配置自动创建默认管理员。
 *
 * <p>账号 / 密码来自 {@link BootstrapProperties}（{@code apiweb.bootstrap.*}），
 * 不再硬编码；可通过环境变量 {@code BOOTSTRAP_ADMIN_USERNAME} /
 * {@code BOOTSTRAP_ADMIN_PASSWORD} 覆盖，或设置
 * {@code apiweb.bootstrap.enabled=false} 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final BootstrapProperties bootstrap;
    /** 复用全局密码编码器，避免各处 new 出不同强度的实例 */
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!bootstrap.isEnabled()) {
            log.info("已关闭默认管理员自动创建（apiweb.bootstrap.enabled=false）");
            return;
        }
        Long count = userMapper.selectCount(null);
        if (count == null || count == 0) {
            UserEntity admin = new UserEntity();
            admin.setUsername(bootstrap.getAdminUsername());
            admin.setPasswordHash(passwordEncoder.encode(bootstrap.getAdminPassword()));
            admin.setRole(Role.ADMIN_CODE);
            userMapper.insert(admin);
            log.info("已创建默认管理员账号: {}（请及时修改密码）", bootstrap.getAdminUsername());
        }
    }
}
