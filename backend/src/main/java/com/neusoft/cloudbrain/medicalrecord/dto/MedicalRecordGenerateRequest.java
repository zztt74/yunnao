package com.neusoft.cloudbrain.medicalrecord.dto;

import jakarta.validation.constraints.NotNull;

/**
 * AI 病历生成请求
 *
 * 输入来自问诊内容，AI 生成结构化病历草稿
 */
public record MedicalRecordGenerateRequest(
        @NotNull(message = "就诊 ID 不能为空")
        Long encounterId,

        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        String physicalExamination,
        java.util.List<String> preliminaryDiagnoses,
        String treatmentSuggestion) {
}
