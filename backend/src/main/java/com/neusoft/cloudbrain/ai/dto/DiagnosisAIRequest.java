package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 辅助诊断请求
 *
 * 输入来自 13_AI能力集成AI任务书.md 第3.2节：
 * 患者主诉、问诊记录和历史信息（最小化必要上下文）。
 */
public record DiagnosisAIRequest(
        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        String physicalExamination,
        String patientAgeRange,
        String patientGender) {
}
