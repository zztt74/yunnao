package com.neusoft.cloudbrain.common.exception;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 统一响应结构和 HTTP 状态码（来自 30_接口数据与错误契约.md 第7节）：
 * - 400 参数错误
 * - 401 未登录
 * - 403 无权限
 * - 404 资源不存在
 * - 409 业务冲突
 * - 429 请求过于频繁
 * - 500 系统异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.warn("业务异常: code={}, message={}, traceId={}", ex.getCode(), ex.getMessage(), traceId);

        HttpStatus httpStatus = HttpStatus.resolve(ex.getHttpStatus());
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage(), traceId));
    }

    /**
     * 参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("参数校验失败: {}, traceId={}", message, traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_FAILED", message, traceId));
    }

    /**
     * 认证异常（未登录）
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.warn("认证失败: {}, traceId={}", ex.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("AUTH_TOKEN_REVOKED", "未登录或 Token 已失效", traceId));
    }

    /**
     * 权限异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.warn("权限不足: {}, traceId={}", ex.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("PERMISSION_DENIED", "无权限访问", traceId));
    }

    /**
     * SecurityException（AuthService 抛出）
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(
            SecurityException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        String message = ex.getMessage();

        // 解析 "CODE:message" 格式
        String code = "SYSTEM_INTERNAL_ERROR";
        int httpStatus = 500;

        if (message != null) {
            int idx = message.indexOf(':');
            if (idx > 0) {
                code = message.substring(0, idx);
                message = message.substring(idx + 1);
            }

            // 根据错误码确定 HTTP 状态码
            httpStatus = resolveHttpStatus(code);
        }

        log.warn("安全异常: code={}, message={}, traceId={}", code, message, traceId);
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(code, message, traceId));
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        String message = ex.getMessage();

        String code = "VALIDATION_FAILED";
        int idx = message != null ? message.indexOf(':') : -1;
        if (idx > 0) {
            code = message.substring(0, idx);
            message = message.substring(idx + 1);
        }

        log.warn("参数错误: code={}, message={}, traceId={}", code, message, traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(code, message, traceId));
    }

    /**
     * 其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.error("系统异常: traceId={}", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("SYSTEM_INTERNAL_ERROR", "系统内部错误", traceId));
    }

    /**
     * 根据错误码解析 HTTP 状态码
     */
    private int resolveHttpStatus(String code) {
        if (code == null) return 500;

        return switch (code) {
            case "AUTH_INVALID_CREDENTIALS" -> 401;
            case "AUTH_ACCOUNT_DISABLED" -> 401;
            case "AUTH_ACCOUNT_LOCKED" -> 401;
            case "AUTH_TOKEN_REVOKED" -> 401;
            case "AUTH_PASSWORD_CHANGE_REQUIRED" -> 403;
            case "AUTH_LOGIN_RATE_LIMITED" -> 429;
            case "PERMISSION_DENIED" -> 403;
            case "VALIDATION_FAILED" -> 400;
            default -> 500;
        };
    }

    /**
     * 获取 traceId
     */
    private String getTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        return traceId != null ? traceId.toString() : null;
    }
}
