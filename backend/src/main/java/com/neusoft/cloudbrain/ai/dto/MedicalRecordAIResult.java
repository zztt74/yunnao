package com.neusoft.cloudbrain.ai.dto;

/**
 * AI 病历生成结果
 *
 * 输出字段来自 13_AI能力集成AI任务书.md 第3.3节（病历生成能力）：
 * 主诉、现病史、既往史、体格检查、初步诊断和治疗建议。
 *
 * 规则：
 * - AI 只能生成草稿
 * - 不得编造输入中不存在的事实
 * - 缺失信息留空或明确标记
 * - 正式病历必须医生确认
 */
public record MedicalRecordAIResult(
        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        String physicalExamination,
        String preliminaryDiagnosis,
        String treatmentSuggestion) {

    /**
     * 向后兼容构造函数（7 参数，含 disclaimer）。
     *
     * 13_AI能力集成AI任务书.md 第3.3节冻结的 AI 输出 Schema 为 6 个字段，不含 disclaimer。
     * 但医疗业务模块（medicalrecord，本任务禁止修改）及其测试仍以 7 参数构造，
     * 故保留此构造函数，忽略传入的 disclaimer，改用 {@link #disclaimer()} 固定安全声明。
     */
    public MedicalRecordAIResult(
            String chiefComplaint,
            String presentIllness,
            String pastHistory,
            String physicalExamination,
            String preliminaryDiagnosis,
            String treatmentSuggestion,
            String disclaimer) {
        this(chiefComplaint, presentIllness, pastHistory, physicalExamination,
                preliminaryDiagnosis, treatmentSuggestion);
    }

    /**
     * 固定免责声明（供 MedicalRecordService 拼接病历内容使用）。
     *
     * 说明：13_AI能力集成AI任务书.md 第3.3节冻结的 AI 输出 Schema 为 6 个字段，
     * 不含 disclaimer。但医疗业务模块（medicalrecord，本任务禁止修改）依赖
     * disclaimer() 拼接"【说明】"段落，故保留此方法返回固定安全声明，
     * 保持向后兼容且不突破修改边界。
     */
    public String disclaimer() {
        return "本病历草稿由 AI 辅助生成，仅供医生参考，需医生确认后形成正式病历";
    }
}
