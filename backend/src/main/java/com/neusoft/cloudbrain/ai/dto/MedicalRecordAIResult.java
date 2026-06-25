package com.neusoft.cloudbrain.ai.dto;

/**
 * AI 病历生成结果
 *
 * 输出字段来自 32_AI能力契约规范.md 第3节（病历生成能力）：
 * 主诉、现病史、既往史、体格检查、初步诊断和治疗建议。
 *
 * 规则：
 * - AI 只能生成草稿
 * - 不得编造输入中不存在的事实
 * - 正式病历必须医生确认
 */
public record MedicalRecordAIResult(
        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        String physicalExamination,
        String preliminaryDiagnosis,
        String treatmentSuggestion,
        String disclaimer) {
}
