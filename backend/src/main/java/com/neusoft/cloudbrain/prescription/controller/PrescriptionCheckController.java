package com.neusoft.cloudbrain.prescription.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionCheckRequest;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionCheckResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionResponse;
import com.neusoft.cloudbrain.prescription.service.PrescriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 处方审核兼容接口（B4）
 *
 * 课程任务四示例路径为 /api/prescription/check。
 * 当前真实路径为 /api/prescriptions（复数），且 AI 审核在创建处方时自动触发。
 * 本接口为兼容入口，显式触发或读取现有处方审核结果。
 *
 * 支持传入 prescriptionId，返回该处方已有审核结果。
 * 返回内容包含风险等级、建议、警告、规则检查摘要、AI 审核状态。
 */
@RestController
@RequestMapping("/api/prescription")
public class PrescriptionCheckController {

    private final PrescriptionService prescriptionService;

    public PrescriptionCheckController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    /**
     * AI 处方辅助审核
     *
     * 传入 prescriptionId，返回该处方的审核结果。
     * - 已有审核结果：直接返回
     * - 无审核结果：返回当前处方详情，review 为空（前端可提示重新创建或等待自动审核）
     *
     * 保持确定性规则优先，AI 不得降低确定性规则判定出的风险等级。
     * AI 失败时仍返回规则审核结果和降级说明（review.aiStatus=FAILED）。
     */
    @PostMapping("/check")
    public ApiResponse<PrescriptionCheckResponse> check(
            @Valid @RequestBody PrescriptionCheckRequest request,
            HttpServletRequest httpRequest) {
        PrescriptionResponse prescription = prescriptionService.getPrescriptionById(request.prescriptionId());

        PrescriptionCheckResponse response = PrescriptionCheckResponse.from(prescription);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
