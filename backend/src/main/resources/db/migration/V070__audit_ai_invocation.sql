-- ============================================================
-- Phase 13-14 - 统计与审计模块
-- 表：audit_log, ai_invocation, ai_invocation_attempt
-- 依据：
--   - contracts/34_数据库设计基线.md 第4.6节 AI 与审计
--   - product/11_功能需求.md 第15节 诊疗数据统计、第16节 操作日志与 AI 调用记录
--   - contracts/32_AI能力契约规范.md 第5节 结构化输出和记录
-- ============================================================

-- ------------------------------------------------------------
-- 审计日志表
-- 记录关键业务操作、数据变更、安全相关操作和管理操作
-- 不记录密码、Token、API Key 或不必要的患者隐私
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人 ID（系统操作时为 NULL）',
    `operator_type` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '操作人类型：USER-用户, SYSTEM-系统',
    `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人用户名（冗余便于查询）',
    `action` VARCHAR(64) NOT NULL COMMENT '操作动作，如 PRESCRIPTION_CONFIRM',
    `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型，如 PRESCRIPTION',
    `target_id` BIGINT DEFAULT NULL COMMENT '目标记录 ID',
    `details` VARCHAR(1024) DEFAULT NULL COMMENT '操作详情（JSON 或文本，已脱敏）',
    `result` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果：SUCCESS-成功, FAILURE-失败',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '失败原因（result=FAILURE 时填写）',
    `ip_address` VARCHAR(64) DEFAULT NULL COMMENT '客户端 IP',
    `user_agent` VARCHAR(255) DEFAULT NULL COMMENT '客户端 User-Agent',
    `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪 ID',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_audit_log_operator_id` (`operator_id`),
    INDEX `idx_audit_log_action` (`action`),
    INDEX `idx_audit_log_target` (`target_type`, `target_id`),
    INDEX `idx_audit_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';

-- ------------------------------------------------------------
-- AI 调用记录表
-- 一次业务 AI 调用只创建一条 ai_invocation
-- AI 成功率按 ai_invocation 统计，重试不重复进入分母
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_invocation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `capability` VARCHAR(32) NOT NULL COMMENT 'AI 能力：TRIAGE-分诊, DIAGNOSIS-诊断, MEDICAL_RECORD-病历, PRESCRIPTION_REVIEW-处方审核, RESULT_INTERPRETATION-结果解读',
    `business_type` VARCHAR(32) DEFAULT NULL COMMENT '业务类型，如 ENCOUNTER, PRESCRIPTION',
    `business_id` BIGINT DEFAULT NULL COMMENT '业务记录 ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '调用状态：PENDING-进行中, SUCCESS-成功, FAILED-失败',
    `error_type` VARCHAR(64) DEFAULT NULL COMMENT '错误类型（失败时填写）',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '错误信息（已脱敏）',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '总耗时（毫秒）',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT 'Provider 请求次数（含重试）',
    `operator_id` BIGINT DEFAULT NULL COMMENT '触发调用的用户 ID',
    `started_at` DATETIME(3) NOT NULL COMMENT '调用开始时间',
    `finished_at` DATETIME(3) DEFAULT NULL COMMENT '调用结束时间',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    INDEX `idx_ai_invocation_capability` (`capability`),
    INDEX `idx_ai_invocation_status` (`status`),
    INDEX `idx_ai_invocation_business` (`business_type`, `business_id`),
    INDEX `idx_ai_invocation_started_at` (`started_at`),
    INDEX `idx_ai_invocation_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用记录表';

-- ------------------------------------------------------------
-- AI 调用尝试表
-- 每次 Provider 请求或重试创建一条 ai_invocation_attempt
-- 原始响应与解析结果分开记录，不保存 API Key
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_invocation_attempt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `invocation_id` BIGINT NOT NULL COMMENT '所属 AI 调用记录 ID',
    `provider` VARCHAR(64) NOT NULL COMMENT 'Provider 标识，如 MOCK, OPENAI',
    `model` VARCHAR(64) DEFAULT NULL COMMENT '模型标识',
    `prompt_version` VARCHAR(32) DEFAULT NULL COMMENT 'Prompt 版本',
    `status` VARCHAR(16) NOT NULL COMMENT '尝试状态：SUCCESS-成功, FAILED-失败, TIMEOUT-超时',
    `http_status` INT DEFAULT NULL COMMENT 'HTTP 状态码',
    `error_type` VARCHAR(64) DEFAULT NULL COMMENT '错误类型',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '错误信息（已脱敏）',
    `request_summary` VARCHAR(512) DEFAULT NULL COMMENT '请求摘要（已脱敏，不含完整 Prompt）',
    `response_summary` VARCHAR(1024) DEFAULT NULL COMMENT '响应摘要（已脱敏，不含原始完整响应）',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '本次尝试耗时（毫秒）',
    `attempt_index` INT NOT NULL COMMENT '尝试序号（从 1 开始）',
    `started_at` DATETIME(3) NOT NULL COMMENT '开始时间',
    `finished_at` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    PRIMARY KEY (`id`),
    INDEX `idx_ai_invocation_attempt_invocation_id` (`invocation_id`),
    INDEX `idx_ai_invocation_attempt_status` (`status`),
    INDEX `idx_ai_invocation_attempt_started_at` (`started_at`),
    CONSTRAINT `fk_ai_attempt_invocation` FOREIGN KEY (`invocation_id`) REFERENCES `ai_invocation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用尝试记录表';
