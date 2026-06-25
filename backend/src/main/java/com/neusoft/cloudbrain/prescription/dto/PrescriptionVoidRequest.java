package com.neusoft.cloudbrain.prescription.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 处方作废请求
 *
 * 规则：
 * - CONFIRMED 后只能作废，不能物理删除
 * - 作废必须记录原因
 */
public record PrescriptionVoidRequest(
        @NotBlank(message = "作废原因不能为空")
        String reason) {
}
