package com.neusoft.cloudbrain.doctor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 医生创建请求
 *
 * 校验规则（B-HW-05）：
 * - username：必填，最长 64
 * - password：必填，8-64 位
 * - departmentId：必填
 * - name：必填，最长 64
 * - title：必填，必须为 CHIEF/DEPUTY_CHIEF/ATTENDING/RESIDENT
 * - phone：选填，11 位手机号
 * - email：选填，合法邮箱格式
 */
public record DoctorCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过 64")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度必须在 8-64 之间")
        String password,

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

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确，需为 11 位国内手机号")
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,

        String education,

        Integer experienceYears,

        String introduction
) {
}
