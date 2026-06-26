package com.neusoft.cloudbrain.ai.exception;

/**
 * AI Provider 调用异常
 *
 * 触发场景（来自 13_AI能力集成AI任务书.md 第8节）：
 * - 请求超时
 * - HTTP 5xx 服务端错误
 * - HTTP 4xx 客户端错误（如鉴权失败）
 * - 网络异常
 *
 * 重试策略：
 * - retryable=true：超时或 5xx，可按 max-retries 重试
 * - retryable=false：4xx 或其他不可恢复错误，不重试
 */
public class AIProviderException extends RuntimeException {

    private final boolean retryable;
    private final Integer httpStatus;

    public AIProviderException(String message, boolean retryable, Integer httpStatus) {
        super(message);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public AIProviderException(String message, boolean retryable, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    /**
     * 是否可重试（超时或 5xx 返回 true）
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * HTTP 状态码（网络异常时为 null）
     */
    public Integer getHttpStatus() {
        return httpStatus;
    }
}
