-- 阶段5：检查检验与病历
-- 对应 Phase 9-10：检查检验管理 + AI 病历生成与病历管理
-- Flyway 版本区间 V040-V049
--
-- 文档参考：
-- - product/12_业务流程与状态机.md 第8节(病历状态)、第10节(检查检验状态)
-- - contracts/34_数据库设计基线.md 第4.3节(检查、检验与病历)
-- - product/11_功能需求.md 第10节(检查与检验管理)、第11节(AI 病历生成与病历管理)

-- ============================================================
-- 检查检验申请表
-- ============================================================
-- 状态流转（来自 12_业务流程与状态机.md 第10节）：
-- ORDERED → IN_PROGRESS         执行中
-- IN_PROGRESS → RESULT_ENTERED  结果录入
-- RESULT_ENTERED → REVIEWED     医生审核
-- ORDERED → CANCELLED           取消
-- IN_PROGRESS → CANCELLED       取消
-- RESULT_ENTERED → IN_PROGRESS  退回重录（需记录原因）
-- 终态：CANCELLED、REVIEWED
-- 患者只能查看 REVIEWED 结果
CREATE TABLE IF NOT EXISTS `examination_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `encounter_id` BIGINT NOT NULL COMMENT '就诊 ID',
    `patient_id` BIGINT NOT NULL COMMENT '患者 ID（冗余便于查询）',
    `doctor_id` BIGINT NOT NULL COMMENT '开立医生 ID',
    `order_type` VARCHAR(16) NOT NULL COMMENT '申请类型：EXAMINATION-检查，LABORATORY-检验',
    `item_code` VARCHAR(64) DEFAULT NULL COMMENT '项目编码',
    `item_name` VARCHAR(128) NOT NULL COMMENT '项目名称',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ORDERED' COMMENT '状态：ORDERED-已开立，IN_PROGRESS-执行中，RESULT_ENTERED-结果已录入，REVIEWED-已审核，CANCELLED-已取消',
    `ordered_at` DATETIME(3) NOT NULL COMMENT '开立时间',
    `in_progress_at` DATETIME(3) DEFAULT NULL COMMENT '进入执行时间',
    `result_entered_at` DATETIME(3) DEFAULT NULL COMMENT '结果录入时间',
    `reviewed_at` DATETIME(3) DEFAULT NULL COMMENT '审核时间',
    `cancelled_at` DATETIME(3) DEFAULT NULL COMMENT '取消时间',
    `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    `return_reason` VARCHAR(255) DEFAULT NULL COMMENT '退回重录原因（RESULT_ENTERED → IN_PROGRESS）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_examination_order_encounter_id` (`encounter_id`),
    INDEX `idx_examination_order_patient_id` (`patient_id`),
    INDEX `idx_examination_order_doctor_id` (`doctor_id`),
    INDEX `idx_examination_order_status` (`status`),
    CONSTRAINT `fk_examination_order_encounter` FOREIGN KEY (`encounter_id`) REFERENCES `encounter` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_examination_order_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_examination_order_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查检验申请';

-- ============================================================
-- 检查检验结果表
-- ============================================================
-- 规则：
-- - AI 解读不能修改原始数据
-- - 未审核结果不向患者展示
-- - REVIEWED 后不得直接覆盖原始结果
-- - 一个申请对应一条结果（uk_examination_result_order_id）
CREATE TABLE IF NOT EXISTS `examination_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '申请 ID（唯一）',
    `result_text` TEXT NOT NULL COMMENT '原始结果文本',
    `normal_range` VARCHAR(512) DEFAULT NULL COMMENT '参考范围',
    `conclusion` VARCHAR(512) DEFAULT NULL COMMENT '结论',
    `abnormal_flag` VARCHAR(16) DEFAULT NULL COMMENT '异常标记：NORMAL-正常，ABNORMAL-异常，CRITICAL-危急值',
    `entered_by` BIGINT DEFAULT NULL COMMENT '录入人 ID',
    `reviewed_by` BIGINT DEFAULT NULL COMMENT '审核医生 ID',
    -- AI 解读（异步，不阻塞业务）
    `ai_interpretation` TEXT DEFAULT NULL COMMENT 'AI 解读结果（通俗解释）',
    `ai_abnormal_items` VARCHAR(512) DEFAULT NULL COMMENT 'AI 标记的异常项（逗号分隔）',
    `ai_follow_up_advice` TEXT DEFAULT NULL COMMENT 'AI 随访建议',
    `ai_status` VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED' COMMENT 'AI 解读状态：NOT_REQUESTED-未请求，PENDING-进行中，SUCCESS-成功，FAILED-失败',
    `ai_failure_reason` VARCHAR(255) DEFAULT NULL COMMENT 'AI 解读失败原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_examination_result_order_id` (`order_id`),
    CONSTRAINT `fk_examination_result_order` FOREIGN KEY (`order_id`) REFERENCES `examination_order` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查检验结果';

-- ============================================================
-- 病历表
-- ============================================================
-- 状态流转（来自 12_业务流程与状态机.md 第8节）：
-- DRAFT → CONFIRMED            医生手工草稿确认
-- AI_GENERATED → CONFIRMED     AI 草稿医生确认
-- DRAFT ↔ AI_GENERATED         来源切换（非正式确认）
-- AMENDED 为扩展版本保留状态，基础版本不得进入
--
-- 规则：
-- - AI 只能生成 DRAFT 或 AI_GENERATED
-- - CONFIRMED 必须由医生完成
-- - AI 原始草稿永久保留，不可被覆盖
-- - 基础版本每个 Encounter 只能有一条当前有效的 CONFIRMED 记录
CREATE TABLE IF NOT EXISTS `medical_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `encounter_id` BIGINT NOT NULL COMMENT '就诊 ID',
    `patient_id` BIGINT NOT NULL COMMENT '患者 ID（冗余便于查询）',
    `doctor_id` BIGINT NOT NULL COMMENT '医生 ID',
    `content` TEXT NOT NULL COMMENT '病历内容（结构化文本）',
    `source` VARCHAR(16) NOT NULL COMMENT '来源：DOCTOR-医生，AI-AI 生成',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿，AI_GENERATED-AI 草稿，CONFIRMED-已确认，AMENDED-已修订（扩展版本）',
    `created_by` BIGINT NOT NULL COMMENT '创建人 ID',
    `confirmed_by` BIGINT DEFAULT NULL COMMENT '确认医生 ID',
    `confirmed_at` DATETIME(3) DEFAULT NULL COMMENT '确认时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_medical_record_encounter_id` (`encounter_id`),
    INDEX `idx_medical_record_patient_id` (`patient_id`),
    INDEX `idx_medical_record_doctor_id` (`doctor_id`),
    INDEX `idx_medical_record_status` (`status`),
    CONSTRAINT `fk_medical_record_encounter` FOREIGN KEY (`encounter_id`) REFERENCES `encounter` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_medical_record_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_medical_record_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='病历';
