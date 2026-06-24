-- V021: 重复挂号唯一约束
-- 防止同一患者对同一排班重复挂号（非取消状态）
-- 使用函数索引：当 status <> 'CANCELLED' 时建立唯一约束，CANCELLED 状态允许多条记录
-- 对应文档：11_功能需求.md 第7节 - 同一患者同一排班不能重复挂号
-- 对应文档：30_接口数据与错误契约.md - APPOINTMENT_DUPLICATED 409

ALTER TABLE `appointment`
    ADD UNIQUE KEY `uk_appointment_patient_schedule_active` (
        `patient_id`,
        `schedule_id`,
        (CASE WHEN `status` <> 'CANCELLED' THEN 1 ELSE NULL END)
    );
