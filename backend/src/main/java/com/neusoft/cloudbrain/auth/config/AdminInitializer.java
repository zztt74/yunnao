package com.neusoft.cloudbrain.auth.config;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 初始管理员初始化器
 *
 * 规则：
 * - 仅在管理员不存在时执行初始化
 * - 用户名和密码来自环境变量或配置文件
 * - 首次登录必须修改密码（mustChangePassword=true）
 * - 默认角色为 ADMIN
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final AdminInitProperties adminInitProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    @Override
    @Transactional
    public void run(String... args) {
        if (!userAccountRepository.existsByUsername(adminInitProperties.getUsername())) {
            log.info("正在创建初始管理员账号: {}", adminInitProperties.getUsername());

            // 创建 ADMIN 角色（如果不存在）
            Role adminRole = Role.builder()
                    .name("ADMIN")
                    .description("系统管理员")
                    .build();

            UserAccount adminUser = UserAccount.builder()
                    .username(adminInitProperties.getUsername())
                    .passwordHash(passwordEncoder.encode(adminInitProperties.getPassword()))
                    .enabled(true)
                    .mustChangePassword(true) // 强制首次登录改密
                    .roles(Set.of(adminRole))
                    .build();

            userAccountRepository.save(adminUser);

            log.info("初始管理员账号创建成功，请使用该账号登录并立即修改密码");
        } else {
            log.info("管理员账号已存在，跳过初始化");
        }
    }
}
