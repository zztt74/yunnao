package com.neusoft.cloudbrain.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import com.neusoft.cloudbrain.audit.entity.AuditLog;
import com.neusoft.cloudbrain.audit.service.AuditService;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuditController 集成测试
 *
 * 验证修复问题2（Controller 返回 DTO 而非 Entity）：
 * - 响应 JSON 中不应包含 Entity 内部字段（如 version 等 JPA 注解字段）
 * - DTO 时间字段应输出 ISO 8601 带偏移格式（+08:00）
 *
 * 验证修复问题1（分页参数 page 从 1 开始）：
 * - 默认 page=1，传入 page=2 转换为 0-based offset=1
 *
 * 契约依据：30_接口数据与错误契约.md、40_工程开发规范.md §2
 */
@DisplayName("AuditController - DTO 序列化与分页参数测试")
class AuditControllerTest {

    private MockMvc mockMvc;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = Mockito.mock(AuditService.class);
        // 配置带 JavaTimeModule 的 ObjectMapper，模拟生产环境 Jackson 配置
        // （application.yml: spring.jackson.serialization.write-dates-as-timestamps=false）
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(auditService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
    }

    // ============== DTO 序列化测试 ==============

    @Test
    @DisplayName("审计日志查询 - 应返回 DTO 而非 Entity（不含 version 字段）")
    void queryLogs_shouldReturnDtoNotEntity() throws Exception {
        AuditLog log = AuditLog.builder()
                .id(1L)
                .operatorId(100L)
                .operatorType("USER")
                .operatorName("admin")
                .action("LOGIN")
                .targetType("USER_ACCOUNT")
                .targetId(100L)
                .result("SUCCESS")
                .traceId("trace-001")
                .createdAt(LocalDateTime.of(2026, 6, 25, 10, 0, 0))
                .build();
        Page<AuditLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1);
        when(auditService.findByTimeRange(any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isOk())
                // DTO 字段应存在
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].operatorId").value(100))
                .andExpect(jsonPath("$.data.items[0].operatorName").value("admin"))
                .andExpect(jsonPath("$.data.items[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.data.items[0].traceId").value("trace-001"))
                // 时间字段应为 ISO 8601 带偏移格式
                .andExpect(jsonPath("$.data.items[0].createdAt").value(
                        org.hamcrest.Matchers.containsString("+08:00")))
                // 不应包含 Entity 内部的 JPA 字段（无 version 字段）
                .andExpect(jsonPath("$.data.items[0].version").doesNotExist());
    }

    @Test
    @DisplayName("审计日志查询 - 空结果应正确序列化")
    void queryLogs_emptyResult_shouldSerialize() throws Exception {
        Page<AuditLog> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);
        when(auditService.findByTimeRange(any(), any(), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("AI 调用详情 - 应返回 DTO 且时间字段带偏移")
    void getInvocation_shouldReturnDtoWithOffsetTime() throws Exception {
        AIInvocation invocation = AIInvocation.builder()
                .id(1L)
                .capability("TRIAGE")
                .businessType("TRIAGE")
                .businessId(50L)
                .status("SUCCESS")
                .attemptCount(1)
                .operatorId(100L)
                .startedAt(LocalDateTime.of(2026, 6, 25, 10, 0, 0))
                .finishedAt(LocalDateTime.of(2026, 6, 25, 10, 0, 5))
                .createdAt(LocalDateTime.of(2026, 6, 25, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 6, 25, 10, 0, 5))
                .build();
        when(auditService.getInvocation(1L)).thenReturn(invocation);

        mockMvc.perform(get("/api/audit/ai/invocations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.capability").value("TRIAGE"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.startedAt").value(
                        org.hamcrest.Matchers.containsString("+08:00")))
                .andExpect(jsonPath("$.data.finishedAt").value(
                        org.hamcrest.Matchers.containsString("+08:00")))
                // Entity 内部字段不应存在
                .andExpect(jsonPath("$.data.version").doesNotExist());
    }

    @Test
    @DisplayName("AI 调用尝试记录 - 应返回 DTO 列表")
    void getInvocationAttempts_shouldReturnDtoList() throws Exception {
        AIInvocationAttempt attempt = AIInvocationAttempt.builder()
                .id(1L)
                .invocationId(1L)
                .provider("MOCK")
                .model("mock-v1")
                .status("SUCCESS")
                .attemptIndex(1)
                .startedAt(LocalDateTime.of(2026, 6, 25, 10, 0, 0))
                .finishedAt(LocalDateTime.of(2026, 6, 25, 10, 0, 5))
                .build();
        when(auditService.getInvocationAttempts(1L)).thenReturn(List.of(attempt));

        mockMvc.perform(get("/api/audit/ai/invocations/1/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].provider").value("MOCK"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].startedAt").value(
                        org.hamcrest.Matchers.containsString("+08:00")))
                .andExpect(jsonPath("$.data[0].version").doesNotExist());
    }

    // ============== 分页参数测试 ==============

    @Test
    @DisplayName("审计日志查询 - 默认 page=1（0-based offset=0）")
    void queryLogs_defaultPage_shouldBe1() throws Exception {
        Page<AuditLog> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);
        when(auditService.findByTimeRange(any(), any(), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isOk());

        Pageable captured = captureTimeRangePageable();
        org.assertj.core.api.Assertions.assertThat(captured.getPageNumber())
                .as("默认 page=1 应转换为 0-based offset=0")
                .isZero();
        org.assertj.core.api.Assertions.assertThat(captured.getPageSize())
                .as("默认 size 应为 20")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("审计日志查询 - page=2 应转换为 0-based offset=1")
    void queryLogs_page2_shouldConvertToOffset1() throws Exception {
        Page<AuditLog> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(auditService.findByTimeRange(any(), any(), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/audit/logs").param("page", "2"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captureTimeRangePageable().getPageNumber())
                .as("page=2 应转换为 0-based offset=1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("审计日志查询 - 按操作人查询时 page 参数正确转换")
    void queryLogs_byOperator_pageShouldConvert() throws Exception {
        Page<AuditLog> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(auditService.findByOperator(eq(100L), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/audit/logs")
                        .param("operatorId", "100")
                        .param("page", "2"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(auditService).findByOperator(eq(100L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber())
                .as("按操作人查询时 page=2 应转换为 0-based offset=1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("审计日志查询 - 响应中 page 应为 1-based（用户视角）")
    void queryLogs_responsePageShouldBe1Based() throws Exception {
        // Service 返回 0-based page=1（第 2 页）
        Page<AuditLog> page = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 50);
        when(auditService.findByTimeRange(any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/audit/logs").param("page", "2"))
                .andExpect(status().isOk())
                // 响应给前端的 page 应为 2（1-based），不是 1（0-based）
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.total").value(50));
    }

    private Pageable captureTimeRangePageable() {
        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(auditService).findByTimeRange(any(), any(), captor.capture());
        return captor.getValue();
    }
}
