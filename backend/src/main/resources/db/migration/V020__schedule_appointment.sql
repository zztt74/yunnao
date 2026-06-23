-- 阶段3：排班与挂号
-- 对应 Phase 5-6：排班和挂号
-- Flyway 版本区间 V020-V029

-- ============================================================
-- 排班表
-- ============================================================
CREATE TABLE IF NOT EXISTS `schedule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doctor_id` BIGINT NOT NULL COMMENT '医生 ID',
    `department_id` BIGINT NOT NULL COMMENT '科室 ID',
    `schedule_date` DATE NOT NULL COMMENT '排班日期',
    `start_time` DATETIME(3) NOT NULL COMMENT '开始时间',
    `end_time` DATETIME(3) NOT NULL COMMENT '结束时间',
    `max_appointments` INT NOT NULL COMMENT '最大号源数',
    `booked_count` INT NOT NULL DEFAULT 0 COMMENT '已预约数',
    `status` VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE-可预约，FULL-已满，CANCELLED-已取消，COMPLETED-已结束',
    `cancelled_at` DATETIME(3) DEFAULT NULL COMMENT '取消时间',
    `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_schedule_doctor_id` (`doctor_id`),
    INDEX `idx_schedule_department_id` (`department_id`),
    INDEX `idx_schedule_date` (`schedule_date`),
    INDEX `idx_schedule_status` (`status`),
    CONSTRAINT `fk_schedule_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_schedule_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排班';

-- ============================================================
-- 挂号表
-- ============================================================
CREATE TABLE IF NOT EXISTS `appointment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `patient_id` BIGINT NOT NULL COMMENT '患者 ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排班 ID',
    `doctor_id` BIGINT NOT NULL COMMENT '医生 ID',
    `appointment_number` VARCHAR(32) NOT NULL COMMENT '挂号号',
    `status` VARCHAR(16) NOT NULL DEFAULT 'BOOKED' COMMENT '状态：BOOKED-已预约，CHECKED_IN-已签到，IN_PROGRESS-就诊中，WAITING_EXAM-等待检查，COMPLETED-已完成，CANCELLED-已取消，NO_SHOW-爽约',
    `booked_at` DATETIME(3) NOT NULL COMMENT '预约时间',
    `check_in_time` DATETIME(3) DEFAULT NULL COMMENT '签到时间',
    `cancellation_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    `cancellation_source` VARCHAR(16) DEFAULT NULL COMMENT '取消来源：PATIENT-患者取消，SCHEDULE-排班取消，ADMIN-管理员取消',
    `cancelled_at` DATETIME(3) DEFAULT NULL COMMENT '取消时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_appointment_number` (`appointment_number`),
    INDEX `idx_appointment_patient_id` (`patient_id`),
    INDEX `idx_appointment_schedule_id` (`schedule_id`),
    INDEX `idx_appointment_doctor_id` (`doctor_id`),
    INDEX `idx_appointment_status` (`status`),
    CONSTRAINT `fk_appointment_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_appointment_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedule` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_appointment_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='挂号';
