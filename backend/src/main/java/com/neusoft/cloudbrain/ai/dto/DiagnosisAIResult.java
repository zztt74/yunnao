package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 辅助诊断结果
 *
 * 输出字段来自 13_AI能力集成AI任务书.md 第3.2节：
 * possibleDiagnoses, evidence, missingInformation, riskFactors, suggestedExaminations, disclaimer
 *
 * 规则：
 * - AI 输出是候选建议，不自动写入正式诊断
 * - 医生必须确认最终诊断
 * - AI 信息不足时必须说明
 * - 不得使用 source=DOCTOR
 */
public record DiagnosisAIResult(
        List<PossibleDiagnosis> possibleDiagnoses,
        List<String> evidence,
        List<String> missingInformation,
        List<String> riskFactors,
        List<String> suggestedExaminations,
        String disclaimer) {

    /**
     * 候选诊断
     */
    public record PossibleDiagnosis(
            String diagnosisCode,
            String diagnosisName,
            String confidence,
            String explanation) {
    }
}
