package com.neusoft.cloudbrain.common.api;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("SUCCESS", "操作成功", data, traceId);
    }

    public static <T> ApiResponse<T> error(
            String code,
            String message,
            T data,
            String traceId) {
        return new ApiResponse<>(code, message, data, traceId);
    }
}
