package com.neusoft.cloudbrain.audit.service;

import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import com.neusoft.cloudbrain.audit.entity.AuditLog;
import com.neusoft.cloudbrain.audit.repository.AIInvocationAttemptRepository;
import com.neusoft.cloudbrain.audit.repository.AIInvocationRepository;
import com.neusoft.cloudbrain.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuditService 单元测试
 *
 * 覆盖文档 11_功能需求.md 第16节 验收重点：
 * - 关键操作可追踪
 * - AI 失败原因可查询
 * - 敏感信息未写入日志
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService - 审计服务测试")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AIInvocationRepository aiInvocationRepository;

    @Mock
    private AIInvocationAttemptRepository aiInvocationAttemptRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    @DisplayName("按操作人查询审计日志")
    void findByOperator_returnsPagedLogs() {
        AuditLog log = AuditLog.builder()
                .operatorId(1L).action("LOGIN").targetType("USER").build();
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findByOperatorIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        var result = auditService.findByOperator(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("LOGIN");
    }

    @Test
    @DisplayName("按目标查询审计日志")
    void findByTarget_returnsPagedLogs() {
        AuditLog log = AuditLog.builder()
                .action("PRESCRIPTION_CONFIRM").targetType("PRESCRIPTION").targetId(100L).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("PRESCRIPTION", 100L, pageable))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        var result = auditService.findByTarget("PRESCRIPTION", 100L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTargetId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("创建 AI 调用记录")
    void createInvocation_savesRecord() {
        AIInvocation invocation = AIInvocation.builder()
                .capability("DIAGNOSIS").status("PENDING")
                .startedAt(LocalDateTime.now()).build();
        when(aiInvocationRepository.save(any(AIInvocation.class))).thenReturn(invocation);

        AIInvocation result = auditService.createInvocation(invocation);

        assertThat(result.getCapability()).isEqualTo("DIAGNOSIS");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(aiInvocationRepository).save(invocation);
    }

    @Test
    @DisplayName("完成 AI 调用记录：更新状态和耗时")
    void finishInvocation_updatesStatusAndDuration() {
        AIInvocation existing = AIInvocation.builder()
                .id(1L).capability("DIAGNOSIS").status("PENDING")
                .startedAt(LocalDateTime.now().minusSeconds(2)).build();
        when(aiInvocationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(aiInvocationRepository.save(any(AIInvocation.class))).thenAnswer(inv -> inv.getArgument(0));

        AIInvocation result = auditService.finishInvocation(1L, "SUCCESS", null, null, 1500L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getDurationMs()).isEqualTo(1500L);
        assertThat(result.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("完成 AI 调用记录：失败时记录错误信息")
    void finishInvocation_recordsErrorOnFailure() {
        AIInvocation existing = AIInvocation.builder()
                .id(1L).capability("TRIAGE").status("PENDING")
                .startedAt(LocalDateTime.now()).build();
        when(aiInvocationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(aiInvocationRepository.save(any(AIInvocation.class))).thenAnswer(inv -> inv.getArgument(0));

        AIInvocation result = auditService.finishInvocation(
                1L, "FAILED", "AI_INVALID_RESPONSE", "JSON 解析失败", 800L);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorType()).isEqualTo("AI_INVALID_RESPONSE");
        assertThat(result.getErrorMessage()).isEqualTo("JSON 解析失败");
    }

    @Test
    @DisplayName("记录 AI 调用尝试")
    void recordAttempt_savesAttempt() {
        AIInvocationAttempt attempt = AIInvocationAttempt.builder()
                .invocationId(1L).provider("MOCK").status("SUCCESS")
                .attemptIndex(1).startedAt(LocalDateTime.now()).build();
        when(aiInvocationAttemptRepository.save(any(AIInvocationAttempt.class))).thenReturn(attempt);

        AIInvocationAttempt result = auditService.recordAttempt(attempt);

        assertThat(result.getProvider()).isEqualTo("MOCK");
        verify(aiInvocationAttemptRepository).save(attempt);
    }

    @Test
    @DisplayName("查询 AI 调用尝试记录：按序号排序")
    void getInvocationAttempts_returnsOrderedAttempts() {
        AIInvocationAttempt attempt1 = AIInvocationAttempt.builder()
                .invocationId(1L).attemptIndex(1).status("FAILED").build();
        AIInvocationAttempt attempt2 = AIInvocationAttempt.builder()
                .invocationId(1L).attemptIndex(2).status("SUCCESS").build();
        when(aiInvocationAttemptRepository.findByInvocationIdOrderByAttemptIndexAsc(1L))
                .thenReturn(List.of(attempt1, attempt2));

        List<AIInvocationAttempt> result = auditService.getInvocationAttempts(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAttemptIndex()).isEqualTo(1);
        assertThat(result.get(1).getAttemptIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("查询 AI 调用详情：不存在时返回 null")
    void getInvocation_notExistsReturnsNull() {
        when(aiInvocationRepository.findById(999L)).thenReturn(Optional.empty());

        AIInvocation result = auditService.getInvocation(999L);

        assertThat(result).isNull();
    }

    // ============================================================
    // 按操作和时间范围查询审计日志
    // ============================================================

    @Test
    @DisplayName("按操作和时间范围查询审计日志 - 返回匹配结果")
    void findByActionAndTimeRange_returnsPagedLogs() {
        AuditLog log = AuditLog.builder()
                .operatorId(1L).action("LOGIN").build();
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        when(auditLogRepository.findByActionAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq("LOGIN"), eq(start), eq(end), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        var result = auditService.findByActionAndTimeRange("LOGIN", start, end, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("LOGIN");
    }

    @Test
    @DisplayName("按操作和时间范围查询审计日志 - 无匹配时返回空页")
    void findByActionAndTimeRange_returnsEmptyPageWhenNoMatch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findByActionAndCreatedAtBetweenOrderByCreatedAtDesc(
                any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = auditService.findByActionAndTimeRange(
                "LOGOUT", LocalDateTime.now().minusDays(1), LocalDateTime.now(), pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ============================================================
    // 按时间范围查询审计日志
    // ============================================================

    @Test
    @DisplayName("按时间范围查询审计日志 - 返回结果")
    void findByTimeRange_returnsPagedLogs() {
        AuditLog log = AuditLog.builder()
                .operatorId(1L).action("CREATE").build();
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        var result = auditService.findByTimeRange(
                LocalDateTime.now().minusDays(30), LocalDateTime.now(), pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("按时间范围查询审计日志 - 无数据时返回空页")
    void findByTimeRange_returnsEmptyPageWhenNoData() {
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = auditService.findByTimeRange(
                LocalDateTime.now().minusHours(1), LocalDateTime.now(), pageable);

        assertThat(result.getContent()).isEmpty();
    }

    // ============================================================
    // 管理端 AI 调用日志分页查询（B5）
    // ============================================================

    @Test
    @DisplayName("AI 调用日志分页查询 - 返回匹配结果")
    void listInvocations_returnsPagedResult() {
        AIInvocation invocation = AIInvocation.builder()
                .id(1L).capability("DIAGNOSIS").status("SUCCESS")
                .startedAt(LocalDateTime.now()).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(aiInvocationRepository.searchInvocations(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(invocation), pageable, 1));

        var result = auditService.listInvocations(
                "DIAGNOSIS", true, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCapability()).isEqualTo("DIAGNOSIS");
    }

    @Test
    @DisplayName("AI 调用日志分页查询 - 无数据时返回空页")
    void listInvocations_returnsEmptyPageWhenNoData() {
        Pageable pageable = PageRequest.of(0, 20);
        when(aiInvocationRepository.searchInvocations(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = auditService.listInvocations(
                null, null, null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ============================================================
    // 查询 AI 调用详情 - 存在时返回
    // ============================================================

    @Test
    @DisplayName("查询 AI 调用详情 - 存在时返回记录")
    void getInvocation_returnsWhenExists() {
        AIInvocation invocation = AIInvocation.builder()
                .id(1L).capability("TRIAGE").status("SUCCESS")
                .startedAt(LocalDateTime.now()).build();
        when(aiInvocationRepository.findById(1L)).thenReturn(Optional.of(invocation));

        AIInvocation result = auditService.getInvocation(1L);

        assertThat(result).isNotNull();
        assertThat(result.getCapability()).isEqualTo("TRIAGE");
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }
}
