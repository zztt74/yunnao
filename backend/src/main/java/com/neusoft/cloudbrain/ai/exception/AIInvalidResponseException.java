package com.neusoft.cloudbrain.ai.exception;

/**
 * AI 响应非法异常
 *
 * 触发场景（来自 32_AI能力契约规范.md 第5节）：
 * - AI 返回的内容不是合法 JSON
 * - 缺少必填字段
 * - 枚举值不在受控范围内
 *
 * 处理策略：
 * - 映射为错误码 AI_INVALID_RESPONSE
 * - 不无限修复模型输出
 * - 不重试（与超时/5xx 不同）
 */
public class AIInvalidResponseException extends RuntimeException {

    /**
     * 对应错误码（来自 33_错误码与时间规范.md 第3节 AI_* 分类）
     */
    public static final String CODE = "AI_INVALID_RESPONSE";

    public AIInvalidResponseException(String message) {
        super(message);
    }

    public AIInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
