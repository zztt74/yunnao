package com.neusoft.cloudbrain.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 安全配置验证器
 *
 * 启动时检查必要的安全配置：
 * - JWT 密钥长度至少 32 字节
 * - 管理员初始密码已配置
 * - 数据库密码已配置
 *
 * 缺少必需安全配置时拒绝启动
 *
 * 实现说明：
 *   使用 @PostConstruct 在 Bean 初始化阶段校验，确保 Web 服务器开始监听端口前
 *   即抛出异常阻止应用启动（参见 30_接口数据与错误契约.md 11.2:
 *   "生产或演示环境缺少 JWT 密钥、管理员初始密码或数据库密码时必须拒绝启动"）。
 *   此前使用 ApplicationReadyEvent 会在端口已监听后才校验，存在短暂窗口期。
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

    @PostConstruct
    public void validateSecurityConfig() {
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
