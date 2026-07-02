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
 * 审计日志实体
 *
 * 用途（来自 11_功能需求.md 第16节）：
 * - 登录日志
 * - 管理员关键操作日志
 * - 病历确认日志
 * - 处方确认和作废日志
 * - 排班取消日志
 *
 * 核心规则：
 * - 不记录密码、Token、API Key
 * - 患者隐私最小化
 * - 日志不能由普通业务删除
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_operator_id", columnList = "operator_id"),
        @Index(name = "idx_audit_log_action", columnList = "action"),
        @Index(name = "idx_audit_log_target", columnList = "target_type,target_id"),
        @Index(name = "idx_audit_log_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_type", nullable = false, length = 32)
    @Builder.Default
    private String operatorType = "UNKNOWN";

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /**
     * B-HW-10：目标对象名称或摘要（如医生姓名、患者姓名、排班日期、处方编号），
     * 便于审计展示“谁对哪个对象做了什么”。
     */
    @Column(name = "target_name", length = 128)
    private String targetName;

    @Column(length = 1024)
    private String details;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String result = "SUCCESS";

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
