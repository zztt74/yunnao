package com.neusoft.cloudbrain.ai.provider;

/**
 * AI Provider 响应
 *
 * 字段说明（来自 32_AI能力契约规范.md 第5节）：
 * - content：模型返回的原始文本内容（优先 JSON）
 * - mock：是否为 Mock 响应（真实 Provider 为 false）
 * - model：模型标识，用于调用记录（来自 41_质量测试与完成定义.md 第7节 可观测性）
 */
public record AIProviderResponse(
        String content,
        boolean mock,
        String model) {

    /**
     * 兼容旧调用：不指定 model
     */
    public AIProviderResponse(String content, boolean mock) {
        this(content, mock, null);
    }
}
