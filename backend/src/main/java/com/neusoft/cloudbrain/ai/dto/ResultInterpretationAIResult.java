package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 结果解读结果
 *
 * 输出字段来自 13_AI能力集成AI任务书.md 第3.5节（检查检验解读）：
 * 异常项、通俗解释、关注点、随访建议和安全声明。
 *
 * 规则：
 * - 不得修改原始检查数值
 * - 禁止编造输入中不存在的检查结果
 * - 结果仅供辅助参考
 * - 医生必须审核确认
 */
public record ResultInterpretationAIResult(
        List<String> abnormalItems,
        String plainLanguageExplanation,
        List<String> possibleAttentionPoints,
        String followUpSuggestion,
        String disclaimer) {

    /**
     * 向后兼容构造函数（4 参数，不含 possibleAttentionPoints，followUpAdvice 对应 followUpSuggestion）。
     *
     * 13_AI能力集成AI任务书.md 第3.5节冻结的 AI 输出 Schema 为 5 个字段（含 possibleAttentionPoints、
     * followUpSuggestion）。但 examination 业务模块（本任务禁止修改）及其测试仍以 4 参数构造，
     * 且使用 followUpAdvice() 访问器，故保留此构造函数和 {@link #followUpAdvice()} 方法。
     */
    public ResultInterpretationAIResult(
            List<String> abnormalItems,
            String plainLanguageExplanation,
            String followUpAdvice,
            String disclaimer) {
        this(abnormalItems, plainLanguageExplanation, List.of(), followUpAdvice, disclaimer);
    }

    /**
     * 向后兼容访问器：examination 业务模块调用 followUpAdvice() 获取随访建议。
     *
     * @return {@link #followUpSuggestion}
     */
    public String followUpAdvice() {
        return followUpSuggestion;
    }
}
