package com.neusoft.cloudbrain.prescription.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionCreateRequest;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionVoidRequest;
import com.neusoft.cloudbrain.prescription.service.PrescriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 处方接口
 *
 * 状态机接口（来自 12_业务流程与状态机.md 第9节）：
 * - POST   /api/prescriptions                    创建处方（确定性规则 + AI 审核）
 * - POST   /api/prescriptions/{id}/confirm       医生确认（DRAFT → CONFIRMED）
 * - POST   /api/prescriptions/{id}/void          作废处方（CONFIRMED → VOIDED）
 *
 * 查询接口：
 * - GET    /api/prescriptions/{id}               处方详情
 * - GET    /api/prescriptions/encounter/{id}     按就诊 ID 查询处方列表
 * - GET    /api/prescriptions/patient/{patientId} 按患者 ID 查询处方列表（分页）
 * - GET    /api/prescriptions/doctor/{doctorId}  按医生 ID 查询处方列表（分页）
 *
 * 关键规则：
 * - 确定性规则先执行，AI 只解释和补充
 * - AI 不得降低或覆盖确定性高风险
 * - AI 失败允许医生手工确认
 * - 处方可不存在，不得为了完成就诊创建空处方
 * - 已确认处方只能作废，不得物理删除
 */
@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    // ============================================================
    // 状态机接口
    // ============================================================

    /**
     * 创建处方
     *
     * 业务流程：
     * 1. 先执行确定性规则检查（过敏、相互作用、剂量、禁忌）
     * 2. 确定性高风险直接拒绝
     * 3. 保存处方
     * 4. 调用 AI 审核（解释规则结果并补充建议）
     */
    @PostMapping
    public ApiResponse<PrescriptionResponse> createPrescription(
            @Valid @RequestBody PrescriptionCreateRequest request,
            HttpServletRequest httpRequest) {
        PrescriptionResponse response = prescriptionService.createPrescription(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 医生确认处方
     *
     * DRAFT → CONFIRMED
     * 高风险需要二次确认（前端展示风险提示）
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<PrescriptionResponse> confirmPrescription(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        PrescriptionResponse response = prescriptionService.confirmPrescription(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 作废处方
     *
     * CONFIRMED → VOIDED
     * 已确认处方只能作废，不能物理删除
     */
    @PostMapping("/{id}/void")
    public ApiResponse<PrescriptionResponse> voidPrescription(
            @PathVariable Long id,
            @Valid @RequestBody PrescriptionVoidRequest request,
            HttpServletRequest httpRequest) {
        PrescriptionResponse response = prescriptionService.voidPrescription(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    // ============================================================
    // 查询接口
    // ============================================================

    /**
     * 获取处方详情
     */
    @GetMapping("/{id}")
    public ApiResponse<PrescriptionResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        PrescriptionResponse response = prescriptionService.getPrescriptionById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按就诊 ID 查询处方列表
     */
    @GetMapping("/encounter/{encounterId}")
    public ApiResponse<List<PrescriptionResponse>> getByEncounter(
            @PathVariable Long encounterId,
            HttpServletRequest httpRequest) {
        List<PrescriptionResponse> response = prescriptionService.getPrescriptionsByEncounter(encounterId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按患者 ID 查询处方列表（分页）
     */
    @GetMapping("/patient/{patientId}")
    public ApiResponse<Page<PrescriptionResponse>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PrescriptionResponse> response = prescriptionService.getPrescriptionsByPatient(patientId, pageable);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按医生 ID 查询处方列表（分页）
     */
    @GetMapping("/doctor/{doctorId}")
    public ApiResponse<Page<PrescriptionResponse>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PrescriptionResponse> response = prescriptionService.getPrescriptionsByDoctor(doctorId, pageable);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
