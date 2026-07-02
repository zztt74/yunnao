package com.neusoft.cloudbrain.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 单元测试
 *
 * 覆盖三类用例：
 * - 正常情况：success / error 工厂方法在典型输入下的字段值
 * - 异常情况：error 工厂方法的错误码与消息透传
 * - 边界条件：null data / null traceId / null 错误码
 */
@DisplayName("ApiResponse - 统一响应封装测试")
class ApiResponseTest {

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("success - 应返回 SUCCESS 编码与操作成功消息")
    void success_shouldReturnSuccessCodeAndMessage() {
        ApiResponse<String> response = ApiResponse.success("ready", "trace-001");

        assertEquals("SUCCESS", response.code());
        assertEquals("操作成功", response.message());
        assertEquals("ready", response.data());
        assertEquals("trace-001", response.traceId());
    }

    @Test
    @DisplayName("error - 应返回指定错误码与消息，data 为 null")
    void error_shouldReturnGivenCodeAndMessageWithNullData() {
        ApiResponse<Object> response = ApiResponse.error("AUTH_INVALID_CREDENTIALS", "用户名或密码错误", "trace-002");

        assertEquals("AUTH_INVALID_CREDENTIALS", response.code());
        assertEquals("用户名或密码错误", response.message());
        assertNull(response.data());
        assertEquals("trace-002", response.traceId());
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("success - data 为 null 时仍应正常构造（防御性边界）")
    void success_withNullData_shouldStillConstruct() {
        ApiResponse<Object> response = ApiResponse.success(null, "trace-003");

        assertEquals("SUCCESS", response.code());
        assertEquals("操作成功", response.message());
        assertNull(response.data());
        assertEquals("trace-003", response.traceId());
    }

    @Test
    @DisplayName("success - traceId 为 null 时仍应正常构造（日志兜底）")
    void success_withNullTraceId_shouldStillConstruct() {
        ApiResponse<String> response = ApiResponse.success("payload", null);

        assertEquals("SUCCESS", response.code());
        assertEquals("payload", response.data());
        assertNull(response.traceId());
    }

    @Test
    @DisplayName("error - data 始终为 null，即使入参未指定 data")
    void error_dataAlwaysNull() {
        ApiResponse<Object> response = ApiResponse.error("BUSINESS_ERROR", "业务异常", "trace-004");

        assertNull(response.data());
    }

    @Test
    @DisplayName("record 相等性 - 相同字段的两个 ApiResponse 应相等")
    void equals_sameFields_shouldBeEqual() {
        ApiResponse<String> a = ApiResponse.success("data", "trace-005");
        ApiResponse<String> b = ApiResponse.success("data", "trace-005");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("record 不等性 - 不同字段的两个 ApiResponse 应不等")
    void equals_differentFields_shouldNotBeEqual() {
        ApiResponse<String> a = ApiResponse.success("data1", "trace-006");
        ApiResponse<String> b = ApiResponse.success("data2", "trace-006");

        assertNotEquals(a, b);
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("error - 空字符串错误码应被原样透传（不做默认替换）")
    void error_withEmptyCode_shouldPassThrough() {
        ApiResponse<Object> response = ApiResponse.error("", "空错误码", "trace-007");

        assertEquals("", response.code());
        assertEquals("空错误码", response.message());
    }

    @Test
    @DisplayName("error - null 错误码应被原样透传（调用方契约由上游保证）")
    void error_withNullCode_shouldPassThrough() {
        ApiResponse<Object> response = ApiResponse.error(null, "null 错误码", "trace-008");

        assertNull(response.code());
        assertEquals("null 错误码", response.message());
    }
}
