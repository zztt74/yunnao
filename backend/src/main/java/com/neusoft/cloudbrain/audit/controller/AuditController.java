package com.neusoft.cloudbrain.audit.controller;

import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import com.neusoft.cloudbrain.audit.entity.AuditLog;
import com.neusoft.cloudbrain.audit.service.AuditService;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计接口
 *
 * 接口（来自 11_功能需求.md 第16节）：
 * - GET /api/audit/logs                       审计日志查询（支持按操作人、动作、目标、时间筛选）
 * - GET /api/audit/logs/operator/{operatorId} 按操作人查询
 * - GET /api/audit/logs/target/{type}/{id}    按目标查询
 * - GET /api/audit/ai/invocations/{id}        AI 调用详情
 * - GET /api/audit/ai/invocations/{id}/attempts AI 调用尝试记录
 *
 * 关键约束：
 * - 普通患者和医生不能查看系统级日志
 * - 不返回密码、Token、API Key 等敏感信息
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 审计日志查询（综合查询）
     */
    @GetMapping("/logs")
    public ApiResponse<PageResponse<AuditLog>> queryLogs(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> result;

        if (operatorId != null) {
            result = auditService.findByOperator(operatorId, pageable);
        } else if (targetType != null && targetId != null) {
            result = auditService.findByTarget(targetType, targetId, pageable);
        } else if (action != null && startDate != null && endDate != null) {
            result = auditService.findByActionAndTimeRange(
                    action, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable);
        } else if (startDate != null && endDate != null) {
            result = auditService.findByTimeRange(
                    startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable);
        } else {
            // 默认查询最近 30 天
            LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
            LocalDateTime start = end.minusDays(30);
            result = auditService.findByTimeRange(start, end, pageable);
        }

        PageResponse<AuditLog> pageResponse = new PageResponse<>(
                result.getContent(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
        return ApiResponse.success(pageResponse, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * AI 调用详情
     */
    @GetMapping("/ai/invocations/{id}")
    public ApiResponse<AIInvocation> getInvocation(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ApiResponse.success(auditService.getInvocation(id),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * AI 调用尝试记录
     */
    @GetMapping("/ai/invocations/{id}/attempts")
    public ApiResponse<List<AIInvocationAttempt>> getInvocationAttempts(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ApiResponse.success(auditService.getInvocationAttempts(id),
                (String) httpRequest.getAttribute("traceId"));
    }
}
