package com.neusoft.cloudbrain.ai.dto;

import java.util.Collections;
import java.util.List;

/**
 * AI 处方审核结果
 *
 * 输出字段来自 13_AI能力集成AI任务书.md 第3.4节（6 字段）：
 * riskLevel, allergyWarnings, interactionWarnings, dosageWarnings, recommendations, summary
 *
 * 规则：
 * - AI 输出仅供辅助参考，不能自动确认处方
 * - AI 不得降低或覆盖确定性规则命中的风险等级
 * - 高风险需要医生二次确认
 * - 不得输出 approved 状态
 *
 * 向后兼容（禁止修改的 prescription 业务模块依赖旧字段）：
 * - suggestions() 访问器返回 recommendations 值
 * - contraindicationWarnings() 访问器返回空列表（禁忌合并到 allergyWarnings / summary）
 * - disclaimer() 方法返回固定安全声明
 * - 7 参数构造函数兼容旧调用方
 */
public record PrescriptionReviewAIResult(
        String riskLevel,
        List<String> allergyWarnings,
        List<String> interactionWarnings,
        List<String> dosageWarnings,
        String recommendations,
        String summary) {

    /**
     * 向后兼容构造函数（禁止修改的 prescription 业务模块使用）
     *
     * 旧字段顺序：riskLevel, allergyWarnings, interactionWarnings, dosageWarnings,
     * contraindicationWarnings, suggestions, disclaimer
     */
    public PrescriptionReviewAIResult(
            String riskLevel,
            List<String> allergyWarnings,
            List<String> interactionWarnings,
            List<String> dosageWarnings,
            List<String> contraindicationWarnings,
            String suggestions,
            String disclaimer) {
        this(riskLevel, allergyWarnings, interactionWarnings, dosageWarnings,
                suggestions, "");
    }

    /**
     * 固定免责声明（来自 32_AI能力契约规范.md 第6节"包含仅供辅助参考等安全声明"）
     */
    public String disclaimer() {
        return "本审核由 AI 辅助生成，仅供医生参考，不能替代医生专业判断";
    }

    /**
     * 向后兼容访问器：suggestions 映射到 recommendations
     */
    public String suggestions() {
        return recommendations;
    }

    /**
     * 向后兼容访问器：contraindicationWarnings 返回空列表
     * （新 Schema 中禁忌信息合并到 allergyWarnings 或 summary，不再单独输出）
     */
    public List<String> contraindicationWarnings() {
        return Collections.emptyList();
    }
}
