package com.neusoft.cloudbrain.medicalrecord.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordCreateRequest;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordGenerateRequest;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordResponse;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordUpdateRequest;
import com.neusoft.cloudbrain.medicalrecord.service.MedicalRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 病历接口
 *
 * 状态机接口（来自 12_业务流程与状态机.md 第8节）：
 * - POST   /api/medical-records                     创建医生手工草稿
 * - POST   /api/medical-records/ai-generate         AI 生成病历草稿
 * - PUT    /api/medical-records/{id}                更新病历（仅 DRAFT/AI_GENERATED）
 * - POST   /api/medical-records/{id}/confirm        确认病历（DRAFT/AI_GENERATED → CONFIRMED）
 *
 * 查询接口：
 * - GET    /api/medical-records/{id}                病历详情
 * - GET    /api/medical-records/encounter/{encounterId}  按就诊 ID 查询病历列表
 * - GET    /api/medical-records/patient/{patientId}      按患者 ID 查询病历列表（分页）
 * - GET    /api/medical-records/encounter/{encounterId}/confirmed  查询就诊的已确认病历
 */
@RestController
@RequestMapping("/api/medical-records")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    // ============================================================
    // 状态机接口
    // ============================================================

    /**
     * 创建医生手工草稿
     */
    @PostMapping
    public ApiResponse<MedicalRecordResponse> createDraft(
            @Valid @RequestBody MedicalRecordCreateRequest request,
            HttpServletRequest httpRequest) {
        MedicalRecordResponse response = medicalRecordService.createDraft(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * AI 生成病历草稿
     *
     * 业务编排：
     * 1. 收集就诊数据
     * 2. 调用 AI 生成
     * 3. 保存为 AI 草稿（AI_GENERATED）
     * 4. AI 失败抛出异常，允许医生手工填写
     */
    @PostMapping("/ai-generate")
    public ApiResponse<MedicalRecordResponse> generateByAI(
            @Valid @RequestBody MedicalRecordGenerateRequest request,
            HttpServletRequest httpRequest) {
        MedicalRecordResponse response = medicalRecordService.generateByAI(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 更新病历内容
     *
     * 仅 DRAFT 和 AI_GENERATED 状态可更新
     * CONFIRMED 后基础版本不允许修改
     */
    @PutMapping("/{id}")
    public ApiResponse<MedicalRecordResponse> updateRecord(
            @PathVariable Long id,
            @Valid @RequestBody MedicalRecordUpdateRequest request,
            HttpServletRequest httpRequest) {
        MedicalRecordResponse response = medicalRecordService.updateRecord(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 医生确认病历
     *
     * DRAFT → CONFIRMED            医生手工草稿确认
     * AI_GENERATED → CONFIRMED     AI 草稿医生确认
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<MedicalRecordResponse> confirmRecord(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        MedicalRecordResponse response = medicalRecordService.confirmRecord(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    // ============================================================
    // 查询接口
    // ============================================================

    /**
     * 获取病历详情
     */
    @GetMapping("/{id}")
    public ApiResponse<MedicalRecordResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        MedicalRecordResponse response = medicalRecordService.getRecordById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按就诊 ID 查询病历列表
     */
    @GetMapping("/encounter/{encounterId}")
    public ApiResponse<List<MedicalRecordResponse>> getByEncounter(
            @PathVariable Long encounterId,
            HttpServletRequest httpRequest) {
        List<MedicalRecordResponse> response = medicalRecordService.getRecordsByEncounter(encounterId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按患者 ID 查询病历列表（分页）
     */
    @GetMapping("/patient/{patientId}")
    public ApiResponse<PageResponse<MedicalRecordResponse>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<MedicalRecordResponse> response = medicalRecordService.getRecordsByPatient(patientId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询就诊的已确认病历
     */
    @GetMapping("/encounter/{encounterId}/confirmed")
    public ApiResponse<MedicalRecordResponse> getConfirmedByEncounter(
            @PathVariable Long encounterId,
            HttpServletRequest httpRequest) {
        MedicalRecordResponse response = medicalRecordService.getConfirmedRecordByEncounter(encounterId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
