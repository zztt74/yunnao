package com.neusoft.cloudbrain.ai.provider;

/**
 * AI Provider 请求
 *
 * 字段说明（来自 32_AI能力契约规范.md 第5节、第6节）：
 * - capability：AI 能力标识（triage / diagnosis / medical_record / prescription_review / result_interpretation）
 * - sanitizedInput：最小化脱敏后的用户输入（不含患者隐私 ID）
 * - systemPrompt：系统提示词，明确任务边界和输出 Schema
 * - promptVersion：Prompt 版本，与模型配置一同记录
 */
public record AIProviderRequest(
        String capability,
        String sanitizedInput,
        String systemPrompt,
        String promptVersion) {

    /**
     * 兼容旧调用：不指定 systemPrompt 和 promptVersion
     */
    public AIProviderRequest(String capability, String sanitizedInput) {
        this(capability, sanitizedInput, null, null);
    }
}
