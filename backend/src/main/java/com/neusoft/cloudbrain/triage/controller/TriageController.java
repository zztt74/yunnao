package com.neusoft.cloudbrain.triage.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeRequest;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeResponse;
import com.neusoft.cloudbrain.triage.dto.TriageRecordResponse;
import com.neusoft.cloudbrain.triage.service.TriageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<Page<TriageRecordResponse>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TriageRecordResponse> response = triageService.getTriageRecordsByPatient(patientId, pageable);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
