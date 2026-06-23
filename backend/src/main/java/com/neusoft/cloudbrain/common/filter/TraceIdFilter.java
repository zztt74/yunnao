package com.neusoft.cloudbrain.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TraceId 过滤器
 *
 * 规则（来自 30_接口数据与错误契约.md 第3节）：
 * - 接收请求头 X-Trace-Id
 * - 只接受 1 至 64 位字母、数字、短横线或下划线
 * - 缺失或非法时生成新值
 * - 同时写入响应头 X-Trace-Id 和统一响应体
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_ATTR = "traceId";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 校验 traceId 格式
        if (!StringUtils.hasText(traceId) || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 存入请求属性，供 Controller 和异常处理器使用
        request.setAttribute(TRACE_ID_ATTR, traceId);

        // 写入响应头
        response.setHeader(TRACE_ID_HEADER, traceId);

        filterChain.doFilter(request, response);
    }
}
