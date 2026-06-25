-- 阶段4：分诊与就诊
-- 对应 Phase 7-8：AI 分诊与就诊状态机
-- Flyway 版本区间 V030-V039
--
-- 文档参考：
-- - product/12_业务流程与状态机.md 第5节(分诊优先级)、第6节(就诊状态)、第7节(诊断类型与来源)
-- - contracts/34_数据库设计基线.md 第4.2节(排班、分诊与诊疗)
-- - product/11_功能需求.md 第6节(AI 智能问诊与分诊)、第8节(医生工作台与门诊接诊)、第9节(AI 辅助诊断)

-- ============================================================
-- 分诊记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `triage_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `patient_id` BIGINT NOT NULL COMMENT '患者 ID',
    `symptoms` TEXT NOT NULL COMMENT '主诉症状',
    `duration` VARCHAR(32) DEFAULT NULL COMMENT '症状持续时间',
    `supplement` TEXT DEFAULT NULL COMMENT '补充信息',
    -- AI 输出
    `ai_department_code` VARCHAR(32) DEFAULT NULL COMMENT 'AI 推荐科室编码',
    `ai_priority` VARCHAR(16) DEFAULT NULL COMMENT 'AI 优先级：LOW-低，MEDIUM-中，HIGH-高，EMERGENCY-紧急',
    `ai_reason` TEXT DEFAULT NULL COMMENT 'AI 分诊理由',
    `ai_safety_notice` TEXT DEFAULT NULL COMMENT 'AI 安全提示',
    `ai_emergency_suggested` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'AI 是否建议急诊：1-是，0-否',
    `ai_symptom_keywords` VARCHAR(512) DEFAULT NULL COMMENT 'AI 症状关键词（逗号分隔）',
    -- 映射结果
    `mapped_department_id` BIGINT DEFAULT NULL COMMENT '映射到的真实科室 ID',
    `mapping_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '映射状态：PENDING-待映射，MAPPED-已映射，MANUAL-人工选择，FAILED-映射失败',
    -- AI 调用状态
    `ai_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'AI 调用状态：PENDING-待调用，SUCCESS-成功，FAILED-失败',
    `ai_failure_reason` VARCHAR(255) DEFAULT NULL COMMENT 'AI 失败原因',
    `ai_invocation_id` BIGINT DEFAULT NULL COMMENT 'AI 调用记录 ID',
    -- 审计
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_triage_record_patient_id` (`patient_id`),
    INDEX `idx_triage_record_mapping_status` (`mapping_status`),
    INDEX `idx_triage_record_ai_status` (`ai_status`),
    CONSTRAINT `fk_triage_record_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_triage_record_department` FOREIGN KEY (`mapped_department_id`) REFERENCES `department` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分诊记录';

-- ============================================================
-- 就诊表
-- ============================================================
-- 一个挂号最多对应一个就诊（encounter.appointment_id 唯一）
-- 状态流转（来自 12_业务流程与状态机.md 第6节）：
-- CREATED → IN_PROGRESS       开始接诊
-- IN_PROGRESS → WAITING_EXAM  等待检查结果
-- WAITING_EXAM → IN_PROGRESS  检查返回继续诊疗
-- IN_PROGRESS → COMPLETED     就诊完成
-- CREATED → CANCELLED         取消就诊
-- 禁止：IN_PROGRESS、WAITING_EXAM、COMPLETED 不允许取消
CREATE TABLE IF NOT EXISTS `encounter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `appointment_id` BIGINT NOT NULL COMMENT '关联挂号 ID（唯一，一个挂号最多一个就诊）',
    `patient_id` BIGINT NOT NULL COMMENT '患者 ID',
    `doctor_id` BIGINT NOT NULL COMMENT '接诊医生 ID',
    `department_id` BIGINT NOT NULL COMMENT '科室 ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED-已创建，IN_PROGRESS-就诊中，WAITING_EXAM-等待检查，COMPLETED-已完成，CANCELLED-已取消',
    `started_at` DATETIME(3) DEFAULT NULL COMMENT '开始接诊时间',
    `waiting_exam_at` DATETIME(3) DEFAULT NULL COMMENT '进入等待检查时间',
    `completed_at` DATETIME(3) DEFAULT NULL COMMENT '完成就诊时间',
    `cancelled_at` DATETIME(3) DEFAULT NULL COMMENT '取消时间',
    `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_encounter_appointment_id` (`appointment_id`),
    INDEX `idx_encounter_patient_id` (`patient_id`),
    INDEX `idx_encounter_doctor_id` (`doctor_id`),
    INDEX `idx_encounter_department_id` (`department_id`),
    INDEX `idx_encounter_status` (`status`),
    CONSTRAINT `fk_encounter_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_encounter_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_encounter_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_encounter_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='就诊';

-- ============================================================
-- 就诊诊断表
-- ============================================================
-- 诊断隔离原则（来自 12_业务流程与状态机.md 第7节）：
-- - AI 只能产生 AI_SUGGESTION，不得产生正式 FINAL + DOCTOR 记录
-- - 医生确认的最终诊断必须为 type=FINAL、source=DOCTOR
-- - 一个 Encounter 至少有一条医生最终诊断后才能完成
-- - AI 原始结果保存在 AI 调用记录，结构化候选诊断保存为 source=AI_SUGGESTION
-- - 医生诊断保存为 source=DOCTOR，两类记录不得互相覆盖
CREATE TABLE IF NOT EXISTS `encounter_diagnosis` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `encounter_id` BIGINT NOT NULL COMMENT '就诊 ID',
    `diagnosis_code` VARCHAR(32) NOT NULL COMMENT '诊断编码',
    `diagnosis_name` VARCHAR(128) NOT NULL COMMENT '诊断名称',
    `type` VARCHAR(16) NOT NULL COMMENT '类型：PRELIMINARY-初步诊断，FINAL-最终诊断',
    `source` VARCHAR(16) NOT NULL COMMENT '来源：DOCTOR-医生，AI_SUGGESTION-AI 建议',
    `ai_invocation_id` BIGINT DEFAULT NULL COMMENT 'AI 调用记录 ID（source=AI_SUGGESTION 时）',
    `doctor_id` BIGINT DEFAULT NULL COMMENT '确认医生 ID（source=DOCTOR 时）',
    `confirmed_at` DATETIME(3) DEFAULT NULL COMMENT '确认时间',
    `notes` TEXT DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_encounter_diagnosis_encounter_id` (`encounter_id`),
    INDEX `idx_encounter_diagnosis_source` (`source`),
    INDEX `idx_encounter_diagnosis_type` (`type`),
    CONSTRAINT `fk_encounter_diagnosis_encounter` FOREIGN KEY (`encounter_id`) REFERENCES `encounter` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_encounter_diagnosis_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='就诊诊断';
