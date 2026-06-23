package com.neusoft.cloudbrain.common.exception;

/**
 * 业务异常
 *
 * 用于携带错误码和 HTTP 状态码，由全局异常处理器统一处理。
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 从 "CODE:message" 格式的字符串解析创建异常
     */
    public static BusinessException fromCodeMessage(String codeMessage, int httpStatus) {
        int idx = codeMessage.indexOf(':');
        if (idx > 0) {
            return new BusinessException(
                    codeMessage.substring(0, idx),
                    codeMessage.substring(idx + 1),
                    httpStatus
            );
        }
        return new BusinessException("SYSTEM_INTERNAL_ERROR", codeMessage, httpStatus);
    }
}
