package com.neusoft.cloudbrain.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 科室更新请求
 */
public record DepartmentUpdateRequest(
        @NotBlank(message = "科室名称不能为空")
        @Size(max = 64, message = "科室名称长度不能超过 64")
        String name,

        Long parentId,

        Integer level,

        Integer sortOrder,

        String status,

        String description
) {
}
