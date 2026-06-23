package com.neusoft.cloudbrain.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 患者更新请求
 */
public record PatientUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名长度不能超过 64")
        String name,

        @NotBlank(message = "性别不能为空")
        @Pattern(regexp = "^(MALE|FEMALE)$", message = "性别必须为 MALE 或 FEMALE")
        String gender,

        @NotNull(message = "出生日期不能为空")
        LocalDate birthDate,

        @NotBlank(message = "手机号不能为空")
        @Size(max = 20, message = "手机号长度不能超过 20")
        String phone
) {
}
