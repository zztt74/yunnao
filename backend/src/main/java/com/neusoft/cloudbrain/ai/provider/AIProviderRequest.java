package com.neusoft.cloudbrain.ai.provider;

import com.neusoft.cloudbrain.triage.dto.ChatMessage;

import java.util.List;

/**
 * AI Provider 请求
 *
 * 字段说明（来自 32_AI能力契约规范.md 第5节、第6节）：
 * - capability：AI 能力标识（triage / diagnosis / medical_record / prescription_review / result_interpretation）
 * - sanitizedInput：最小化脱敏后的用户输入（不含患者隐私 ID）
 * - systemPrompt：系统提示词，明确任务边界和输出 Schema
 * - promptVersion：Prompt 版本，与模型配置一同记录
 * - history：多轮对话历史（B-HW-07），仅含症状描述（role=USER/ASSISTANT），可为空
 */
public record AIProviderRequest(
        String capability,
        String sanitizedInput,
        String systemPrompt,
        String promptVersion,
        List<ChatMessage> history) {

    /**
     * 兼容旧调用：不指定 systemPrompt、promptVersion 和 history
     */
    public AIProviderRequest(String capability, String sanitizedInput) {
        this(capability, sanitizedInput, null, null, null);
    }

    /**
     * 兼容旧调用：不指定 history（单轮场景）
     */
    public AIProviderRequest(String capability, String sanitizedInput,
                             String systemPrompt, String promptVersion) {
        this(capability, sanitizedInput, systemPrompt, promptVersion, null);
    }
}
