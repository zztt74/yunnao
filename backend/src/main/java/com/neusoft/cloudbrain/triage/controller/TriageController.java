package com.neusoft.cloudbrain.triage.controller;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.common.api.PageUtils;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeRequest;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeResponse;
import com.neusoft.cloudbrain.triage.dto.TriageRecordResponse;
import com.neusoft.cloudbrain.triage.service.TriageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 分诊接口
 *
 * - POST   /api/triage/analyze           AI 分诊分析
 * - GET    /api/triage/{id}              分诊记录详情
 * - GET    /api/triage/patient/{patientId}  患者分诊记录列表
 */
@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    /**
     * AI 分诊分析
     *
     * 患者输入症状，返回 AI 分诊建议、科室映射和推荐排班。
     * AI 失败时返回降级标记，提示转人工选择。
     */
    @PostMapping("/analyze")
    public ApiResponse<TriageAnalyzeResponse> analyze(
            @Valid @RequestBody TriageAnalyzeRequest request,
            HttpServletRequest httpRequest) {
        TriageAnalyzeResponse response = triageService.analyze(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取分诊记录详情
     */
    @GetMapping("/{id}")
    public ApiResponse<TriageRecordResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        TriageRecordResponse response = triageService.getTriageRecordById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询患者分诊记录列表（分页）
     */
    @GetMapping("/patient/{patientId}")
    public ApiResponse<PageResponse<TriageRecordResponse>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<TriageRecordResponse> response = triageService.getTriageRecordsByPatient(patientId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 管理员全量分诊记录查询（B4）
     *
     * 分页查看所有分诊记录，支持按患者、优先级、映射科室、时间范围筛选。
     * 仅管理员可全量查看；患者查看自己的分诊记录走 /patient/{patientId}。
     */
    @GetMapping
    public ApiResponse<PageResponse<TriageRecordResponse>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        int resolvedSize = PageUtils.resolvePageSize(pageSize, size);
        Pageable pageable = PageUtils.toPageable(page, resolvedSize);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
        Page<TriageRecordResponse> response = triageService.listTriageRecords(
                patientId, priority, departmentId, start, end, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 校验管理员权限
     */
    private void checkAdminPermission() {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (!currentUser.roles().contains("ADMIN")) {
            throw new BusinessException("PERMISSION_DENIED", "无权限执行该操作", 403);
        }
    }
}
