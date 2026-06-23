package com.neusoft.cloudbrain.common.api;

/**
 * 统一 API 响应
 *
 * @param <T> 数据类型
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId
) {
    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("SUCCESS", "操作成功", data, traceId);
    }

    /**
     * 创建错误响应
     */
    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
