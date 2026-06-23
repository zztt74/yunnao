package com.neusoft.cloudbrain.department.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 科室响应
 */
public record DepartmentResponse(
        Long id,
        String code,
        String name,
        Long parentId,
        Integer level,
        Integer sortOrder,
        String status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DepartmentResponse> children
) {
}
