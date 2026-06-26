package com.neusoft.cloudbrain.encounter.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterCancelRequest;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisRequest;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterStartRequest;
import com.neusoft.cloudbrain.encounter.service.EncounterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 就诊接口
 *
 * 状态机接口（来自 12_业务流程与状态机.md 第6节）：
 * - POST   /api/encounters/start              开始接诊（CREATED → IN_PROGRESS）
 * - POST   /api/encounters/{id}/wait-exam     等待检查（IN_PROGRESS → WAITING_EXAM）
 * - POST   /api/encounters/{id}/resume        继续诊疗（WAITING_EXAM → IN_PROGRESS）
 * - POST   /api/encounters/{id}/complete      完成就诊（IN_PROGRESS → COMPLETED）
 * - POST   /api/encounters/{id}/cancel        取消就诊（CREATED → CANCELLED）
 *
 * 诊断接口（诊断隔离原则）：
 * - POST   /api/encounters/{id}/diagnoses/ai      添加 AI 候选诊断（AI_SUGGESTION）
 * - POST   /api/encounters/{id}/diagnoses/doctor  添加医生最终诊断（FINAL + DOCTOR）
 * - GET    /api/encounters/{id}/diagnoses         查询就诊诊断列表
 *
 * 查询接口：
 * - GET    /api/encounters/{id}                   就诊详情
 * - GET    /api/encounters/appointment/{id}       按挂号 ID 查询就诊
 * - GET    /api/encounters/doctor/{doctorId}      医生就诊列表
 * - GET    /api/encounters/patient/{patientId}    患者就诊列表
 */
@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    // ============================================================
    // 状态机接口
    // ============================================================

    /**
     * 开始接诊
     */
    @PostMapping("/start")
    public ApiResponse<EncounterResponse> startEncounter(
            @Valid @RequestBody EncounterStartRequest request,
            HttpServletRequest httpRequest) {
        EncounterResponse response = encounterService.startEncounter(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 等待检查结果
     */
    @PostMapping("/{id}/wait-exam")
    public ApiResponse<EncounterResponse> waitForExam(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        EncounterResponse response = encounterService.waitForExam(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 检查返回，继续诊疗
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<EncounterResponse> resumeEncounter(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        EncounterResponse response = encounterService.resumeEncounter(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 完成就诊
     *
     * 前置条件：
     * - 病历已确认
     * - 存在医生最终诊断
     * - 所有检查检验已完成
     * - 处方状态正常（如有）
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<EncounterResponse> completeEncounter(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        EncounterResponse response = encounterService.completeEncounter(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 取消就诊
     *
     * 仅 CREATED 状态可取消。
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<EncounterResponse> cancelEncounter(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) EncounterCancelRequest request,
            HttpServletRequest httpRequest) {
        EncounterCancelRequest cancelRequest = request != null ? request : new EncounterCancelRequest(null);
        EncounterResponse response = encounterService.cancelEncounter(id, cancelRequest);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    // ============================================================
    // 诊断接口（诊断隔离原则）
    // ============================================================

    /**
     * 添加 AI 候选诊断
     *
     * 诊断隔离原则：AI 只能创建 type=PRELIMINARY, source=AI_SUGGESTION
     */
    @PostMapping("/{id}/diagnoses/ai")
    public ApiResponse<EncounterDiagnosisResponse> addAIDiagnosis(
            @PathVariable Long id,
            @Valid @RequestBody EncounterDiagnosisRequest request,
            HttpServletRequest httpRequest) {
        EncounterDiagnosisResponse response = encounterService.addAIDiagnosis(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 添加医生最终诊断
     *
     * 诊断隔离原则：医生诊断必须为 type=FINAL, source=DOCTOR
     */
    @PostMapping("/{id}/diagnoses/doctor")
    public ApiResponse<EncounterDiagnosisResponse> addDoctorDiagnosis(
            @PathVariable Long id,
            @Valid @RequestBody EncounterDiagnosisRequest request,
            HttpServletRequest httpRequest) {
        EncounterDiagnosisResponse response = encounterService.addDoctorDiagnosis(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询就诊诊断列表
     */
    @GetMapping("/{id}/diagnoses")
    public ApiResponse<List<EncounterDiagnosisResponse>> getDiagnoses(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        List<EncounterDiagnosisResponse> response = encounterService.getEncounterDiagnoses(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    // ============================================================
    // 查询接口
    // ============================================================

    /**
     * 获取就诊详情
     */
    @GetMapping("/{id}")
    public ApiResponse<EncounterResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        EncounterResponse response = encounterService.getEncounterById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按挂号 ID 查询就诊
     */
    @GetMapping("/appointment/{appointmentId}")
    public ApiResponse<EncounterResponse> getByAppointmentId(
            @PathVariable Long appointmentId,
            HttpServletRequest httpRequest) {
        EncounterResponse response = encounterService.getEncounterByAppointmentId(appointmentId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询医生就诊列表（分页）
     */
    @GetMapping("/doctor/{doctorId}")
    public ApiResponse<PageResponse<EncounterResponse>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<EncounterResponse> response = encounterService.getEncountersByDoctor(doctorId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询医生某状态的就诊列表
     */
    @GetMapping("/doctor/{doctorId}/status/{status}")
    public ApiResponse<List<EncounterResponse>> getByDoctorAndStatus(
            @PathVariable Long doctorId,
            @PathVariable String status,
            HttpServletRequest httpRequest) {
        List<EncounterResponse> response = encounterService.getEncountersByDoctorAndStatus(doctorId, status);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询患者就诊列表（分页）
     */
    @GetMapping("/patient/{patientId}")
    public ApiResponse<PageResponse<EncounterResponse>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<EncounterResponse> response = encounterService.getEncountersByPatient(patientId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }
}
