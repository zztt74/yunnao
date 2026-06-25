package com.neusoft.cloudbrain.common.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceIdFilter 单元测试
 *
 * 验证规则（来自 30_接口数据与错误契约.md 第3节）：
 * - 合法 X-Trace-Id 原样回传
 * - 缺失或非法时生成新值
 * - 同时写入响应头 X-Trace-Id
 * - 写入请求属性 traceId
 * - 写入 MDC，请求结束后清理
 */
@DisplayName("TraceIdFilter - 请求链路追踪测试")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("合法 X-Trace-Id 应原样写入响应头和请求属性")
    void validTraceId_shouldBePreserved() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "my-trace-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("my-trace-id-123");
        assertThat(chain.getRequest().getAttribute("traceId")).isEqualTo("my-trace-id-123");
    }

    @Test
    @DisplayName("缺失 X-Trace-Id 时应生成新值")
    void missingTraceId_shouldGenerateNew() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String generated = response.getHeader("X-Trace-Id");
        assertThat(generated).isNotBlank();
        assertThat(generated).matches("^[a-zA-Z0-9_-]{1,64}$");
        assertThat(chain.getRequest().getAttribute("traceId")).isEqualTo(generated);
    }

    @Test
    @DisplayName("非法 X-Trace-Id（含特殊字符）应生成新值")
    void illegalTraceId_shouldGenerateNew() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "bad trace id!@#");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String generated = response.getHeader("X-Trace-Id");
        assertThat(generated).isNotBlank();
        assertThat(generated).isNotEqualTo("bad trace id!@#");
        assertThat(generated).matches("^[a-zA-Z0-9_-]{1,64}$");
    }

    @Test
    @DisplayName("超长 X-Trace-Id（超过64位）应生成新值")
    void tooLongTraceId_shouldGenerateNew() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String generated = response.getHeader("X-Trace-Id");
        assertThat(generated).isNotBlank();
        assertThat(generated.length()).isLessThanOrEqualTo(64);
    }

    @Test
    @DisplayName("请求结束后 MDC 应被清理")
    void mdcShouldBeClearedAfterFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "to-be-cleared");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // 过滤器执行完毕后 MDC 应已清理
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("空字符串 X-Trace-Id 应生成新值")
    void emptyTraceId_shouldGenerateNew() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String generated = response.getHeader("X-Trace-Id");
        assertThat(generated).isNotBlank();
        assertThat(generated).matches("^[a-zA-Z0-9_-]{1,64}$");
    }

    @Test
    @DisplayName("下游过滤器内 MDC 应可读取 traceId")
    void mdcShouldBeAvailableInFilterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "downstream-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] captured = new String[1];
        // 下游过滤器捕获 MDC 中的 traceId
        jakarta.servlet.FilterChain chain = (req, resp) -> {
            captured[0] = MDC.get("traceId");
        };

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo("downstream-trace");
    }
}
