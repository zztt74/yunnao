package com.neusoft.cloudbrain.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 安全配置验证器
 *
 * 启动时检查必要的安全配置：
 * - JWT 密钥长度至少 32 字节
 * - 管理员初始密码已配置
 * - 数据库密码已配置
 *
 * 缺少必需安全配置时拒绝启动
 */
@Slf4j
@Component
public class SecurityConfigValidator {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${admin.init.password:}")
    private String adminPassword;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void validateSecurityConfig() {
        log.info("正在验证安全配置...");

        // 检查 JWT 密钥
        if (jwtSecret == null || jwtSecret.length() < 32) {
            String errorMsg = "JWT 密钥未配置或长度不足 32 字节，请设置环境变量 JWT_SECRET";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 检查管理员初始密码
        if (adminPassword == null || adminPassword.isBlank()) {
            String errorMsg = "管理员初始密码未配置，请设置环境变量 INITIAL_ADMIN_PASSWORD";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 检查数据库密码
        if (dbPassword == null || dbPassword.isBlank()) {
            String errorMsg = "数据库密码未配置，请设置环境变量 DB_PASSWORD";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        log.info("安全配置验证通过");
    }
}
