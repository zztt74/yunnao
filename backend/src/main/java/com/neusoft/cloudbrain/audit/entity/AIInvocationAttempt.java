package com.neusoft.cloudbrain.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 调用尝试记录实体
 *
 * 规则（来自 contracts/32_AI能力契约规范.md 第5节）：
 * - 每次 Provider 请求或重试对应一条 AIInvocationAttempt
 * - 原始响应与解析结果分开记录
 * - 不保存 API Key
 */
@Entity
@Table(name = "ai_invocation_attempt", indexes = {
        @Index(name = "idx_ai_invocation_attempt_invocation_id", columnList = "invocation_id"),
        @Index(name = "idx_ai_invocation_attempt_status", columnList = "status"),
        @Index(name = "idx_ai_invocation_attempt_started_at", columnList = "started_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIInvocationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invocation_id", nullable = false)
    private Long invocationId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(length = 64)
    private String model;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_type", length = 64)
    private String errorType;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "request_summary", length = 512)
    private String requestSummary;

    @Column(name = "response_summary", length = 1024)
    private String responseSummary;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "attempt_index", nullable = false)
    private Integer attemptIndex;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
