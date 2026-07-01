ALTER TABLE `user_account`
    ADD COLUMN `real_name` VARCHAR(64) DEFAULT NULL COMMENT 'Real name for admin/user management display' AFTER `username`,
    ADD COLUMN `phone` VARCHAR(20) DEFAULT NULL COMMENT 'Contact phone for admin/user management display' AFTER `real_name`,
    ADD COLUMN `email` VARCHAR(128) DEFAULT NULL COMMENT 'Contact email for admin/user management display' AFTER `phone`;
