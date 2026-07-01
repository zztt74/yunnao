package com.neusoft.cloudbrain.doctor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 医生本人资料更新请求
 *
 * 仅允许医生自行更新非敏感资料字段，不允许修改科室、职称、状态。
 */
public record DoctorProfileUpdateRequest(
        @Size(max = 255, message = "擅长方向长度不能超过 255")
        String specialty,

        @Size(max = 64, message = "学历长度不能超过 64")
        String education,

        @Min(value = 0, message = "从业年限不能为负")
        Integer experienceYears,

        String introduction
) {
}
