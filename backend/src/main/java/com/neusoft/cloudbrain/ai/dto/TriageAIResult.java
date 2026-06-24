package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 分诊结果
 *
 * 输出字段来自 13_AI能力集成AI任务书.md 第3.1节：
 * departmentCode, priority, symptomKeywords, reason, safetyNotice, emergencySuggested
 *
 * 规则：
 * - 不得输出医生 ID
 * - 不得输出正式诊断
 * - 结果仅供辅助参考
 */
public record TriageAIResult(
        String departmentCode,
        String priority,
        List<String> symptomKeywords,
        String reason,
        String safetyNotice,
        boolean emergencySuggested) {
}
