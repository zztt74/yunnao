package com.neusoft.cloudbrain.audit.service;

import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import com.neusoft.cloudbrain.audit.entity.AuditLog;
import com.neusoft.cloudbrain.audit.repository.AIInvocationAttemptRepository;
import com.neusoft.cloudbrain.audit.repository.AIInvocationRepository;
import com.neusoft.cloudbrain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计 Service
 *
 * 提供：
 * - 审计日志查询（按操作人、目标、动作、时间范围）
 * - AI 调用记录查询
 * - AI 调用记录创建与更新（供 AI 模块调用）
 *
 * 核心规则（来自 11_功能需求.md 第16节）：
 * - 不记录密码、Token、API Key
 * - 患者隐私最小化
 * - 普通患者和医生不能查看系统级日志
 * - 日志不能由普通业务删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AIInvocationRepository aiInvocationRepository;
    private final AIInvocationAttemptRepository aiInvocationAttemptRepository;

    // ============================================================
    // 审计日志查询
    // ============================================================

    /**
     * 按操作人查询审计日志
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByOperator(Long operatorId, Pageable pageable) {
        return auditLogRepository.findByOperatorIdOrderByCreatedAtDesc(operatorId, pageable);
    }

    /**
     * 按目标查询审计日志
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByTarget(String targetType, Long targetId, Pageable pageable) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable);
    }

    /**
     * 按动作和时间范围查询审计日志
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByActionAndTimeRange(String action, LocalDateTime start,
                                                    LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByActionAndCreatedAtBetweenOrderByCreatedAtDesc(
                action, start, end, pageable);
    }

    /**
     * 按时间范围查询审计日志
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByTimeRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
    }

    // ============================================================
    // AI 调用记录
    // ============================================================

    /**
     * 创建 AI 调用记录
     *
     * 供 AI 模块在发起业务调用时创建。
     * 一次业务调用只创建一条记录。
     */
    @Transactional
    public AIInvocation createInvocation(AIInvocation invocation) {
        return aiInvocationRepository.save(invocation);
    }

    /**
     * 完成 AI 调用记录
     */
    @Transactional
    public AIInvocation finishInvocation(Long invocationId, String status,
                                          String errorType, String errorMessage,
                                          Long durationMs) {
        AIInvocation invocation = aiInvocationRepository.findById(invocationId)
                .orElseThrow(() -> new IllegalArgumentException("AI 调用记录不存在: " + invocationId));
        invocation.setStatus(status);
        invocation.setErrorType(errorType);
        invocation.setErrorMessage(errorMessage);
        invocation.setDurationMs(durationMs);
        invocation.setFinishedAt(LocalDateTime.now());
        return aiInvocationRepository.save(invocation);
    }

    /**
     * 记录 AI 调用尝试
     */
    @Transactional
    public AIInvocationAttempt recordAttempt(AIInvocationAttempt attempt) {
        return aiInvocationAttemptRepository.save(attempt);
    }

    /**
     * 查询 AI 调用详情（含尝试记录）
     */
    @Transactional(readOnly = true)
    public AIInvocation getInvocation(Long invocationId) {
        return aiInvocationRepository.findById(invocationId).orElse(null);
    }

    /**
     * 查询 AI 调用的尝试记录
     */
    @Transactional(readOnly = true)
    public List<AIInvocationAttempt> getInvocationAttempts(Long invocationId) {
        return aiInvocationAttemptRepository.findByInvocationIdOrderByAttemptIndexAsc(invocationId);
    }
}
