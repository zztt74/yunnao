package com.neusoft.cloudbrain.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 病历创建请求（医生手工草稿）
 */
public record MedicalRecordCreateRequest(
        @NotNull(message = "就诊 ID 不能为空")
        Long encounterId,

        @NotBlank(message = "病历内容不能为空")
        String content) {
}
