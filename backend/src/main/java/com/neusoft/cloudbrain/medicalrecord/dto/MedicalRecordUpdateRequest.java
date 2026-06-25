package com.neusoft.cloudbrain.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 病历更新请求
 *
 * 仅 DRAFT 和 AI_GENERATED 状态可更新
 * CONFIRMED 后基础版本不允许修改
 */
public record MedicalRecordUpdateRequest(
        @NotBlank(message = "病历内容不能为空")
        String content) {
}
