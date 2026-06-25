-- ============================================================
-- Phase 12 - 设备模块
-- 表：device, device_usage, device_status_history
-- ============================================================

-- 设备主表
CREATE TABLE IF NOT EXISTS `device` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(32) NOT NULL COMMENT '设备编码（唯一）',
    `name` VARCHAR(128) NOT NULL COMMENT '设备名称',
    `type` VARCHAR(32) NOT NULL COMMENT '设备类型：MONITOR-监护仪, ULTRASOUND-超声, CT-CT, MRI-MRI, X_RAY-X光, ECG-心电图, OTHER-其他',
    `department_id` BIGINT DEFAULT NULL COMMENT '所属科室 ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' COMMENT '设备状态：AVAILABLE-可用, IN_USE-使用中, ABNORMAL-异常, MAINTENANCE-维护中, DISABLED-停用',
    `purchase_date` DATE DEFAULT NULL COMMENT '采购日期',
    `warranty_until` DATE DEFAULT NULL COMMENT '保修截止日期',
    `last_maintenance` DATETIME(3) DEFAULT NULL COMMENT '最后维护时间',
    `location` VARCHAR(128) DEFAULT NULL COMMENT '存放位置',
    `manufacturer` VARCHAR(128) DEFAULT NULL COMMENT '制造商',
    `model` VARCHAR(64) DEFAULT NULL COMMENT '型号',
    `serial_number` VARCHAR(64) DEFAULT NULL COMMENT '序列号',
    `notes` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_code` (`code`),
    INDEX `idx_device_department_id` (`department_id`),
    INDEX `idx_device_status` (`status`),
    INDEX `idx_device_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备主表';

-- 设备使用记录表
CREATE TABLE IF NOT EXISTS `device_usage` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `device_id` BIGINT NOT NULL COMMENT '设备 ID',
    `encounter_id` BIGINT NOT NULL COMMENT '就诊 ID',
    `used_by` BIGINT NOT NULL COMMENT '使用人 ID（医生/技师）',
    `start_time` DATETIME(3) NOT NULL COMMENT '开始使用时间',
    `end_time` DATETIME(3) DEFAULT NULL COMMENT '结束使用时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'IN_USAGE' COMMENT '使用状态：IN_USAGE-使用中, COMPLETED-已完成, ABORTED-异常中止',
    `notes` VARCHAR(512) DEFAULT NULL COMMENT '使用备注',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    INDEX `idx_device_usage_device_id` (`device_id`),
    INDEX `idx_device_usage_encounter_id` (`encounter_id`),
    INDEX `idx_device_usage_used_by` (`used_by`),
    INDEX `idx_device_usage_status` (`status`),
    INDEX `idx_device_usage_start_time` (`start_time`),
    CONSTRAINT `fk_device_usage_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_device_usage_encounter` FOREIGN KEY (`encounter_id`) REFERENCES `encounter` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备使用记录表';

-- 设备状态变更历史表
CREATE TABLE IF NOT EXISTS `device_status_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `device_id` BIGINT NOT NULL COMMENT '设备 ID',
    `from_status` VARCHAR(16) NOT NULL COMMENT '变更前状态',
    `to_status` VARCHAR(16) NOT NULL COMMENT '变更后状态',
    `operator_id` BIGINT NOT NULL COMMENT '操作人 ID',
    `reason` VARCHAR(255) DEFAULT NULL COMMENT '变更原因',
    `changed_at` DATETIME(3) NOT NULL COMMENT '变更时间',
    PRIMARY KEY (`id`),
    INDEX `idx_device_status_history_device_id` (`device_id`),
    INDEX `idx_device_status_history_changed_at` (`changed_at`),
    CONSTRAINT `fk_device_status_history_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备状态变更历史表';

-- ============================================================
-- 初始数据：系统内固定虚构设备
-- ============================================================

-- 查询内科、外科、放射科的 department_id（避免硬编码）
SET @internal_dept_id = (SELECT id FROM department WHERE code = 'INTERNAL' LIMIT 1);
SET @surgery_dept_id = (SELECT id FROM department WHERE code = 'SURGERY' LIMIT 1);
SET @radiology_dept_id = (SELECT id FROM department WHERE code = 'RADIOLOGY' LIMIT 1);

INSERT INTO `device` (`code`, `name`, `type`, `department_id`, `status`, `purchase_date`, `warranty_until`, `location`, `manufacturer`, `model`, `serial_number`, `notes`, `created_at`) VALUES
-- 监护仪
('DEV-MON-001', '多功能监护仪 #1', 'MONITOR', @internal_dept_id, 'AVAILABLE', '2023-03-15', '2026-03-15', '内科病房 301', 'Philips', 'MX800', 'SN-MON-001', '常规使用', NOW(3)),
('DEV-MON-002', '多功能监护仪 #2', 'MONITOR', @internal_dept_id, 'AVAILABLE', '2023-03-15', '2026-03-15', '内科病房 302', 'Philips', 'MX800', 'SN-MON-002', '常规使用', NOW(3)),
('DEV-MON-003', '便携式监护仪', 'MONITOR', @surgery_dept_id, 'AVAILABLE', '2023-05-20', '2026-05-20', '外科病房 201', 'GE Healthcare', 'CARESCAPE', 'SN-MON-003', '便携式', NOW(3)),

-- 超声设备
('DEV-US-001', '彩色超声诊断仪', 'ULTRASOUND', @radiology_dept_id, 'AVAILABLE', '2022-11-10', '2025-11-10', '超声室 101', 'Siemens', 'ACUSON', 'SN-US-001', '高分辨率', NOW(3)),
('DEV-US-002', '便携式超声仪', 'ULTRASOUND', @surgery_dept_id, 'AVAILABLE', '2023-08-05', '2026-08-05', '外科急诊室', 'Mindray', 'M9', 'SN-US-002', '便携式', NOW(3)),

-- CT 设备
('DEV-CT-001', '64 排 CT 扫描仪', 'CT', @radiology_dept_id, 'AVAILABLE', '2021-06-20', '2024-06-20', 'CT 室 1', 'GE Healthcare', 'Optima CT660', 'SN-CT-001', '需定期维护', NOW(3)),

-- MRI 设备
('DEV-MRI-001', '1.5T 核磁共振仪', 'MRI', @radiology_dept_id, 'AVAILABLE', '2020-09-15', '2023-09-15', 'MRI 室 1', 'Siemens', 'MAGNETOM', 'SN-MRI-001', '已过保修期', NOW(3)),

-- X 光机
('DEV-XRAY-001', '数字 X 光机', 'X_RAY', @radiology_dept_id, 'AVAILABLE', '2022-04-10', '2025-04-10', 'X 光室 1', 'GE Healthcare', 'Definium', 'SN-XRAY-001', '常规使用', NOW(3)),

-- 心电图机
('DEV-ECG-001', '十二导联心电图机 #1', 'ECG', @internal_dept_id, 'AVAILABLE', '2023-02-28', '2026-02-28', '内科检查室', 'GE Healthcare', 'MAC 5500', 'SN-ECG-001', '常规使用', NOW(3)),
('DEV-ECG-002', '十二导联心电图机 #2', 'ECG', @internal_dept_id, 'AVAILABLE', '2023-02-28', '2026-02-28', '内科检查室', 'GE Healthcare', 'MAC 5500', 'SN-ECG-002', '常规使用', NOW(3)),

-- 异常状态设备示例（用于测试）
('DEV-MON-004', '故障监护仪', 'MONITOR', @internal_dept_id, 'ABNORMAL', '2021-01-10', '2024-01-10', '内科库房', 'Philips', 'MX400', 'SN-MON-004', '屏幕显示异常，待维修', NOW(3)),
('DEV-CT-002', '维护中 CT', 'CT', @radiology_dept_id, 'MAINTENANCE', '2020-03-15', '2023-03-15', 'CT 室 2', 'Siemens', 'SOMATOM', 'SN-CT-002', '球管更换中', NOW(3)),

-- 停用设备示例
('DEV-XRAY-002', '停用 X 光机', 'X_RAY', @radiology_dept_id, 'DISABLED', '2018-05-20', '2021-05-20', 'X 光室 2（停用）', 'Shimadzu', 'RADspeed', 'SN-XRAY-002', '设备老旧，已停用', NOW(3));
