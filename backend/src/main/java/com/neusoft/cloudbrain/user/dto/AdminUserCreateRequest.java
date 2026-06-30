package com.neusoft.cloudbrain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建用户请求
 *
 * - role=ADMIN：只建账号，仅需 username/password
 * - role=DOCTOR：需带医生档案字段（departmentId、doctorName 必填），同步建医生档案
 * - role=PATIENT：不支持，请走患者自助注册接口
 */
public record AdminUserCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过 64")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        String password,

        @NotBlank(message = "角色不能为空")
        String role,

        // ===== 医生档案字段（仅 role=DOCTOR 时必填）=====
        Long departmentId,

        @Size(max = 64, message = "医生姓名长度不能超过 64")
        String doctorName,

        @Size(max = 32, message = "职称长度不能超过 32")
        String doctorTitle,

        @Size(max = 255, message = "擅长方向长度不能超过 255")
        String specialty,

        @Size(max = 64, message = "学历长度不能超过 64")
        String education,

        Integer experienceYears,

        String introduction
) {
}
