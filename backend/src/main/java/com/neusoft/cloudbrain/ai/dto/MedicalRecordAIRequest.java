package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 病历生成请求
 *
 * 输入字段来自 32_AI能力契约规范.md 第3节（病历生成能力）：
 * 主诉、现病史、既往史、体格检查、初步诊断和治疗建议。
 *
 * B5 扩展：consultationTranscript 问诊对话记录。
 * - 当医生录入问诊对话文本时，AI 从对话中提取结构化字段。
 * - 不包含患者 ID、姓名、手机号等隐私信息（最小化原则）。
 */
public record MedicalRecordAIRequest(
        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        String physicalExamination,
        List<String> preliminaryDiagnoses,
        String treatmentSuggestion,
        String consultationTranscript,
        String departmentCode) {
}
