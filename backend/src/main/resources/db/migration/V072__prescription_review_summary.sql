-- 为 prescription_review 表添加 summary 字段
-- 对应 AI 6 字段 Schema 中的 summary（AI 审核摘要）
-- 存储 AI 对处方审核的整体解读和结论摘要
ALTER TABLE prescription_review
    ADD COLUMN summary TEXT;
