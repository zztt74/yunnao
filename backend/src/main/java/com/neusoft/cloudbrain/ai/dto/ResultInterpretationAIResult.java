package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 结果解读结果
 *
 * 输出字段来自 32_AI能力契约规范.md 第3节（结果解读能力）：
 * 异常项、通俗解释和随访建议。
 *
 * 规则：
 * - 不得修改原始检查数值
 * - 结果仅供辅助参考
 * - 医生必须审核确认
 */
public record ResultInterpretationAIResult(
        List<String> abnormalItems,
        String plainLanguageExplanation,
        String followUpAdvice,
        String disclaimer) {
}
