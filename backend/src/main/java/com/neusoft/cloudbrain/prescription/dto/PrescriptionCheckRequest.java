package com.neusoft.cloudbrain.prescription.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 处方审核请求（B4 兼容接口）
 *
 * 当前实现优先支持 prescriptionId 方式：
 * 传入已存在的处方 ID，返回该处方的审核结果。
 */
public record PrescriptionCheckRequest(
        @NotNull(message = "处方 ID 不能为空")
        Long prescriptionId) {
}
