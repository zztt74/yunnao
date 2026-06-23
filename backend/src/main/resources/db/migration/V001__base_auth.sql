-- 认证与权限基础表
-- 对应 Phase 1-2：JWT 认证体系

-- 角色枚举表
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(32) NOT NULL COMMENT '角色名称：PATIENT、DOCTOR、ADMIN',
    `description` VARCHAR(128) DEFAULT NULL COMMENT '角色描述',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色';

-- 用户账号表
CREATE TABLE IF NOT EXISTS `user_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希，cost=12',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    `account_non_locked` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否未锁定：1-未锁定，0-已锁定',
    `account_non_expired` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否未过期：1-未过期，0-已过期',
    `credentials_non_expired` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '凭证是否未过期：1-未过期，0-已过期',
    `must_change_password` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要强制修改密码：1-是，0-否',
    `failed_login_attempts` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `lockout_until` DATETIME(3) DEFAULT NULL COMMENT '锁定截止时间',
    `token_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'Token 版本号，递增使旧 Token 失效',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account_username` (`username`),
    INDEX `idx_user_account_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_account_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `role_id` BIGINT NOT NULL COMMENT '角色 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account_role` (`user_id`, `role_id`),
    INDEX `idx_uar_user_id` (`user_id`),
    INDEX `idx_uar_role_id` (`role_id`),
    CONSTRAINT `fk_uar_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_uar_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

-- 初始化基础角色数据
INSERT INTO `role` (`name`, `description`) VALUES
    ('PATIENT', '患者'),
    ('DOCTOR', '医生'),
    ('ADMIN', '管理员');
