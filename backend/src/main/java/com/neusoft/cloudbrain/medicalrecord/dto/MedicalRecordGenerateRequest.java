package com.neusoft.cloudbrain.medicalrecord.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AI 病历生成请求
 *
 * 输入来自问诊内容，AI 生成结构化病历草稿。
 *
 * B5 扩展：支持问诊对话记录（consultationTranscript）。
 * - 当医生录入问诊对话文本时，AI 从对话中提取主诉、现病史等信息。
 * - 字段为空时保持现有结构化字段生成逻辑。
 */
public record MedicalRecordGenerateRequest(
        @NotNull(message = "就诊 ID 不能为空")
        Long encounterId,

        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        String physicalExamination,
        java.util.List<String> preliminaryDiagnoses,
        String treatmentSuggestion,

        @Size(max = 5000, message = "问诊对话记录不能超过 5000 字")
        String consultationTranscript) {
}
