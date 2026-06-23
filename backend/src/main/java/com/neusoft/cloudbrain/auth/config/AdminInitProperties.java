package com.neusoft.cloudbrain.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 初始管理员配置属性
 *
 * 从环境变量读取：
 * - INITIAL_ADMIN_USERNAME：初始管理员用户名
 * - INITIAL_ADMIN_PASSWORD：初始管理员密码
 */
@Data
@Component
@ConfigurationProperties(prefix = "admin.init")
public class AdminInitProperties {

    /** 初始管理员用户名 */
    private String username = "admin";

    /** 初始管理员密码（必须通过环境变量配置，生产环境缺失时拒绝启动） */
    private String password;
}
