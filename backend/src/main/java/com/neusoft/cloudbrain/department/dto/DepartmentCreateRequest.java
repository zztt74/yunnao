package com.neusoft.cloudbrain.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 科室创建请求
 */
public record DepartmentCreateRequest(
        @NotBlank(message = "科室编码不能为空")
        @Size(max = 32, message = "科室编码长度不能超过 32")
        String code,

        @NotBlank(message = "科室名称不能为空")
        @Size(max = 64, message = "科室名称长度不能超过 64")
        String name,

        Long parentId,

        Integer level,

        Integer sortOrder,

        String description
) {
}
