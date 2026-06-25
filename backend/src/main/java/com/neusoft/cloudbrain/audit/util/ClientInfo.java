package com.neusoft.cloudbrain.audit.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端信息工具类
 *
 * 用于审计日志获取客户端 IP 和 User-Agent。
 */
public final class ClientInfo {

    private ClientInfo() {
    }

    /**
     * 获取当前请求的客户端 IP
     */
    public static String getClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return extractClientIp(request);
    }

    /**
     * 获取当前请求的 User-Agent
     */
    public static String getUserAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 255) {
            return ua.substring(0, 255);
        }
        return ua;
    }

    /**
     * 获取当前请求的 traceId
     */
    public static String getTraceId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object traceId = request.getAttribute("traceId");
        return traceId == null ? null : traceId.toString();
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
