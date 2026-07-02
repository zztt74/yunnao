-- B-HW-04: 用户账号新增最近登录时间字段，用于管理端用户列表展示
ALTER TABLE `user_account`
    ADD COLUMN `last_login_at` DATETIME DEFAULT NULL COMMENT '最近一次成功登录时间' AFTER `email`;

-- B-HW-10: 审计日志新增目标名称字段，用于操作日志展示目标对象摘要
ALTER TABLE `audit_log`
    ADD COLUMN `target_name` VARCHAR(128) DEFAULT NULL COMMENT '目标对象名称或摘要，便于审计展示' AFTER `target_id`;
