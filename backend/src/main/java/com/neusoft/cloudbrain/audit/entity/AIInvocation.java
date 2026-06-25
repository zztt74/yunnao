package com.neusoft.cloudbrain.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 调用记录实体
 *
 * 规则（来自 contracts/32_AI能力契约规范.md 第5节 和 contracts/34_数据库设计基线.md 第4.6节）：
 * - 一次业务 AI 调用只创建一条 AIInvocation
 * - AI 成功率按 AIInvocation 统计，重试不重复进入分母
 * - 不保存 API Key
 * - 业务关联使用业务类型和业务记录 ID，不建立指向多个业务表的伪外键
 *
 * 说明：本实体由审计模块持有，用于统计和审计追踪。
 * AI 模块在调用 Provider 时应创建并更新本记录。
 */
@Entity
@Table(name = "ai_invocation", indexes = {
        @Index(name = "idx_ai_invocation_capability", columnList = "capability"),
        @Index(name = "idx_ai_invocation_status", columnList = "status"),
        @Index(name = "idx_ai_invocation_business", columnList = "business_type,business_id"),
        @Index(name = "idx_ai_invocation_started_at", columnList = "started_at"),
        @Index(name = "idx_ai_invocation_operator_id", columnList = "operator_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIInvocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String capability;

    @Column(name = "business_type", length = 32)
    private String businessType;

    @Column(name = "business_id")
    private Long businessId;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "error_type", length = 64)
    private String errorType;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "attempt_count")
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
