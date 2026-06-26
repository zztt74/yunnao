package com.neusoft.cloudbrain.examination.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.examination.dto.ExaminationCancelRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationOrderCreateRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationOrderResponse;
import com.neusoft.cloudbrain.examination.dto.ExaminationResultRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationResultResponse;
import com.neusoft.cloudbrain.examination.dto.ExaminationReturnRequest;
import com.neusoft.cloudbrain.examination.service.ExaminationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 检查检验接口
 *
 * 状态机接口（来自 12_业务流程与状态机.md 第10节）：
 * - POST   /api/examinations                       创建检查检验申请
 * - POST   /api/examinations/{id}/start            开始执行（ORDERED → IN_PROGRESS）
 * - POST   /api/examinations/{id}/result           录入结果（IN_PROGRESS → RESULT_ENTERED）
 * - POST   /api/examinations/{id}/review           医生审核（RESULT_ENTERED → REVIEWED）
 * - POST   /api/examinations/{id}/return           退回重录（RESULT_ENTERED → IN_PROGRESS）
 * - POST   /api/examinations/{id}/cancel           取消申请（ORDERED/IN_PROGRESS → CANCELLED）
 *
 * 查询接口：
 * - GET    /api/examinations/{id}                  申请详情
 * - GET    /api/examinations/encounter/{encounterId}  按就诊 ID 查询申请列表
 * - GET    /api/examinations/patient/{patientId}   按患者 ID 查询申请列表（分页）
 * - GET    /api/examinations/doctor/{doctorId}     按医生 ID 查询申请列表（分页）
 * - GET    /api/examinations/{id}/result           查询结果详情（患者只能查看 REVIEWED）
 */
@RestController
@RequestMapping("/api/examinations")
public class ExaminationController {

    private final ExaminationService examinationService;

    public ExaminationController(ExaminationService examinationService) {
        this.examinationService = examinationService;
    }

    // ============================================================
    // 状态机接口
    // ============================================================

    /**
     * 创建检查检验申请
     */
    @PostMapping
    public ApiResponse<ExaminationOrderResponse> createOrder(
            @Valid @RequestBody ExaminationOrderCreateRequest request,
            HttpServletRequest httpRequest) {
        ExaminationOrderResponse response = examinationService.createOrder(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 开始执行检查检验
     */
    @PostMapping("/{id}/start")
    public ApiResponse<ExaminationOrderResponse> startProgress(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        ExaminationOrderResponse response = examinationService.startProgress(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 录入检查检验结果
     *
     * 业务编排：
     * 1. 保存原始结果
     * 2. 调用 AI 解读（异步，不阻塞业务）
     * 3. AI 失败不抛异常，允许医生手工解读
     */
    @PostMapping("/{id}/result")
    public ApiResponse<ExaminationResultResponse> recordResult(
            @PathVariable Long id,
            @Valid @RequestBody ExaminationResultRequest request,
            HttpServletRequest httpRequest) {
        ExaminationResultResponse response = examinationService.recordResult(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 医生审核结果
     */
    @PostMapping("/{id}/review")
    public ApiResponse<ExaminationResultResponse> reviewResult(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        ExaminationResultResponse response = examinationService.reviewResult(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 退回重录
     *
     * RESULT_ENTERED → IN_PROGRESS 仅用于审核退回，必须记录原因
     */
    @PostMapping("/{id}/return")
    public ApiResponse<ExaminationOrderResponse> returnForReentry(
            @PathVariable Long id,
            @Valid @RequestBody ExaminationReturnRequest request,
            HttpServletRequest httpRequest) {
        ExaminationOrderResponse response = examinationService.returnForReentry(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 取消检查检验申请
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<ExaminationOrderResponse> cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ExaminationCancelRequest request,
            HttpServletRequest httpRequest) {
        ExaminationCancelRequest cancelRequest = request != null ? request : new ExaminationCancelRequest(null);
        ExaminationOrderResponse response = examinationService.cancelOrder(id, cancelRequest);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    // ============================================================
    // 查询接口
    // ============================================================

    /**
     * 获取申请详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ExaminationOrderResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        ExaminationOrderResponse response = examinationService.getOrderById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按就诊 ID 查询申请列表
     */
    @GetMapping("/encounter/{encounterId}")
    public ApiResponse<List<ExaminationOrderResponse>> getByEncounter(
            @PathVariable Long encounterId,
            HttpServletRequest httpRequest) {
        List<ExaminationOrderResponse> response = examinationService.getOrdersByEncounter(encounterId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按患者 ID 查询申请列表（分页）
     */
    @GetMapping("/patient/{patientId}")
    public ApiResponse<PageResponse<ExaminationOrderResponse>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<ExaminationOrderResponse> response = examinationService.getOrdersByPatient(patientId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按医生 ID 查询申请列表（分页）
     */
    @GetMapping("/doctor/{doctorId}")
    public ApiResponse<PageResponse<ExaminationOrderResponse>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<ExaminationOrderResponse> response = examinationService.getOrdersByDoctor(doctorId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询结果详情
     *
     * 患者只能查看 REVIEWED 结果
     */
    @GetMapping("/{id}/result")
    public ApiResponse<ExaminationResultResponse> getResult(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        ExaminationResultResponse response = examinationService.getResultByOrderId(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
