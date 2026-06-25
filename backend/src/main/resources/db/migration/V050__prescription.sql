-- 阶段6：处方与确定性用药规则
-- 对应 Phase 11：处方管理与 AI 处方审核
-- Flyway 版本区间 V050-V059
--
-- 文档参考：
-- - product/12_业务流程与状态机.md 第9节(处方状态与AI审核状态)
-- - contracts/34_数据库设计基线.md 第4.4节(处方与确定性用药规则)
-- - product/11_功能需求.md 第12节(处方管理与AI处方审核)
--
-- 处方业务状态：DRAFT → CONFIRMED → VOIDED
-- AI 审核状态（独立）：NOT_REQUESTED → PENDING → REVIEWED/FAILED
-- 规则：
-- - 处方业务状态与 AI 审核状态分开保存
-- - 处方明细必须引用系统内固定虚构药品
-- - 已确认处方只能作废，不得物理删除

-- ============================================================
-- 处方主表
-- ============================================================
CREATE TABLE IF NOT EXISTS `prescription` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `encounter_id` BIGINT NOT NULL COMMENT '就诊 ID',
    `patient_id` BIGINT NOT NULL COMMENT '患者 ID（冗余便于查询）',
    `doctor_id` BIGINT NOT NULL COMMENT '开立医生 ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '处方业务状态：DRAFT-草稿，CONFIRMED-已确认，VOIDED-已作废',
    `ai_review_status` VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED' COMMENT 'AI 审核状态：NOT_REQUESTED-未请求，PENDING-审核中，REVIEWED-已审核，FAILED-审核不通过',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `confirmed_at` DATETIME(3) DEFAULT NULL COMMENT '确认时间',
    `confirmed_by` BIGINT DEFAULT NULL COMMENT '确认医生 ID',
    `voided_at` DATETIME(3) DEFAULT NULL COMMENT '作废时间',
    `voided_by` BIGINT DEFAULT NULL COMMENT '作废操作人 ID',
    `voided_reason` VARCHAR(255) DEFAULT NULL COMMENT '作废原因',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_prescription_encounter_id` (`encounter_id`),
    INDEX `idx_prescription_patient_id` (`patient_id`),
    INDEX `idx_prescription_doctor_id` (`doctor_id`),
    INDEX `idx_prescription_status` (`status`),
    CONSTRAINT `fk_prescription_encounter` FOREIGN KEY (`encounter_id`) REFERENCES `encounter` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_prescription_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_prescription_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='处方主表';

-- ============================================================
-- 处方明细表
-- ============================================================
-- 处方明细必须引用系统内固定虚构药品（drug_code 关联 drug.code）
CREATE TABLE IF NOT EXISTS `prescription_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `prescription_id` BIGINT NOT NULL COMMENT '处方 ID',
    `drug_code` VARCHAR(32) NOT NULL COMMENT '药品编码（关联 drug.code）',
    `drug_name` VARCHAR(128) NOT NULL COMMENT '药品名称（冗余）',
    `dosage` VARCHAR(64) NOT NULL COMMENT '剂量（如 1片）',
    `dosage_value` DECIMAL(10, 3) DEFAULT NULL COMMENT '剂量数值（用于规则校验）',
    `frequency` VARCHAR(32) NOT NULL COMMENT '用药频次：QD-每日一次，BID-每日两次，TID-每日三次，QID-每日四次，QN-每晚一次',
    `duration` INT NOT NULL COMMENT '疗程天数',
    `quantity` DECIMAL(10, 3) NOT NULL COMMENT '总数量',
    `instructions` VARCHAR(512) DEFAULT NULL COMMENT '用药说明',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_prescription_item_prescription_id` (`prescription_id`),
    INDEX `idx_prescription_item_drug_code` (`drug_code`),
    CONSTRAINT `fk_prescription_item_prescription` FOREIGN KEY (`prescription_id`) REFERENCES `prescription` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='处方明细';

-- ============================================================
-- 处方 AI 审核记录表
-- ============================================================
-- AI 审核状态独立于处方业务状态
-- 确定性规则命中不得被 AI 输出降级或覆盖
CREATE TABLE IF NOT EXISTS `prescription_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `prescription_id` BIGINT NOT NULL COMMENT '处方 ID',
    `review_status` VARCHAR(16) NOT NULL COMMENT '审核状态：PENDING-审核中，REVIEWED-已审核，FAILED-审核不通过',
    `risk_level` VARCHAR(16) NOT NULL COMMENT '风险等级：SAFE-安全，LOW-低风险，MEDIUM-中风险，HIGH-高风险，CONTRAINDICATED-禁忌',
    `allergy_warnings` TEXT DEFAULT NULL COMMENT '过敏警告（JSON 数组）',
    `interaction_warnings` TEXT DEFAULT NULL COMMENT '相互作用警告（JSON 数组）',
    `dosage_warnings` TEXT DEFAULT NULL COMMENT '剂量警告（JSON 数组）',
    `contraindication_warnings` TEXT DEFAULT NULL COMMENT '禁忌警告（JSON 数组）',
    `suggestions` TEXT DEFAULT NULL COMMENT 'AI 补充建议',
    `rule_check_summary` TEXT DEFAULT NULL COMMENT '确定性规则检查摘要（AI 不得降级）',
    `reviewed_at` DATETIME(3) DEFAULT NULL COMMENT '审核完成时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_prescription_review_prescription_id` (`prescription_id`),
    CONSTRAINT `fk_prescription_review_prescription` FOREIGN KEY (`prescription_id`) REFERENCES `prescription` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='处方 AI 审核记录';
