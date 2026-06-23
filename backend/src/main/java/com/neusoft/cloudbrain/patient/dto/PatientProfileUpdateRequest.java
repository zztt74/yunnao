package com.neusoft.cloudbrain.patient.dto;

import jakarta.validation.constraints.Size;

/**
 * 患者档案更新请求
 */
public record PatientProfileUpdateRequest(
        @Size(max = 255, message = "住址长度不能超过 255")
        String address,

        @Size(max = 64, message = "紧急联系人长度不能超过 64")
        String emergencyContact,

        @Size(max = 20, message = "紧急联系电话长度不能超过 20")
        String emergencyPhone,

        String allergies,

        String medicalHistory
) {
}
