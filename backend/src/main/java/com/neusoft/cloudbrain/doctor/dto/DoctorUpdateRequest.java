package com.neusoft.cloudbrain.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 医生更新请求
 */
public record DoctorUpdateRequest(
        @NotNull(message = "科室 ID 不能为空")
        Long departmentId,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名长度不能超过 64")
        String name,

        @NotBlank(message = "职称不能为空")
        @Pattern(regexp = "^(CHIEF|DEPUTY_CHIEF|ATTENDING|RESIDENT)$",
                message = "职称必须为 CHIEF、DEPUTY_CHIEF、ATTENDING 或 RESIDENT")
        String title,

        @Size(max = 255, message = "擅长方向长度不能超过 255")
        String specialty,

        String status,

        String education,

        Integer experienceYears,

        String introduction
) {
}
