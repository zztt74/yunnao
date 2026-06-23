package com.neusoft.cloudbrain.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全相关配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /** 连续失败锁定阈值（默认 5 次） */
    private int maxFailedAttempts = 5;

    /** 锁定时长（分钟，默认 15 分钟） */
    private int lockoutMinutes = 15;

    /** 登录限流窗口时间（秒，默认 5 分钟） */
    private int rateLimitWindowSeconds = 300;

    /** 登录限流最大失败次数（默认 10 次） */
    private int rateLimitMaxAttempts = 10;
}
