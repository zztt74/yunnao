-- ============================================================
-- 修正 ai_invocation_attempt 外键约束
--
-- 依据：
--   - contracts/30_接口数据与错误契约.md 第9.2节：
--     "AI 调用和审计日志不得物理删除"
--   - V070__audit_ai_invocation.sql 原始约束使用了 ON DELETE CASCADE，
--     违反"正式业务记录不得物理删除"的设计精神。
--
-- 变更内容：
--   将 fk_ai_attempt_invocation 的 ON DELETE 行为从 CASCADE 改为 RESTRICT，
--   从数据库约束层面禁止通过删除 ai_invocation 级联删除 attempt 记录。
--
-- 注意：
--   - 已执行的迁移脚本不得直接修改，因此新增 V071 进行修正。
--   - MySQL 不支持直接 ALTER 外键约束，需先 DROP 再 ADD。
-- ============================================================

ALTER TABLE `ai_invocation_attempt`
    DROP FOREIGN KEY `fk_ai_attempt_invocation`;

ALTER TABLE `ai_invocation_attempt`
    ADD CONSTRAINT `fk_ai_attempt_invocation`
        FOREIGN KEY (`invocation_id`) REFERENCES `ai_invocation` (`id`)
        ON DELETE RESTRICT;
