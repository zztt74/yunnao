package com.neusoft.cloudbrain.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 处方创建请求
 *
 * 业务规则：
 * - 药品明细不能为空（不得为了完成就诊创建空处方）
 * - 药品必须来自系统内固定虚构字典
 */
public record PrescriptionCreateRequest(
        @NotNull(message = "就诊 ID 不能为空")
        Long encounterId,

        @NotEmpty(message = "药品明细不能为空，不得创建空处方")
        @Valid
        List<PrescriptionItemDTO> items) {
}
