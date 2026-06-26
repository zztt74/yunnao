package com.neusoft.cloudbrain.department.service;

import com.neusoft.cloudbrain.department.dto.DepartmentCreateRequest;
import com.neusoft.cloudbrain.department.dto.DepartmentResponse;
import com.neusoft.cloudbrain.department.dto.DepartmentUpdateRequest;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.exception.DepartmentErrorCode;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 科室 Service
 *
 * 功能：
 * - 科室树形结构查询
 * - 科室扁平列表查询
 * - 科室详情查询
 * - 科室新增、编辑、启用和停用
 *
 * 权限规则：
 * - 停用科室不能创建新排班
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    /**
     * 获取科室树形结构（缓存）
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "departmentTree")
    public List<DepartmentResponse> getDepartmentTree() {
        List<Department> allDepartments = departmentRepository.findAll();
        return buildTree(allDepartments);
    }

    /**
     * 获取科室扁平列表（缓存）
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "departmentList")
    public List<DepartmentResponse> getDepartmentList() {
        return departmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取科室详情
     */
    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(DepartmentErrorCode.DEPARTMENT_NOT_FOUND::toException);
        return toResponse(department);
    }

    /**
     * 创建科室
     */
    @Transactional
    @CacheEvict(value = {"departmentTree", "departmentList"}, allEntries = true)
    public DepartmentResponse createDepartment(DepartmentCreateRequest request) {
        // 检查编码唯一性
        if (departmentRepository.existsByCode(request.code())) {
            throw DepartmentErrorCode.DEPARTMENT_CODE_DUPLICATED.toException();
        }

        // 校验父科室
        if (request.parentId() != null) {
            departmentRepository.findById(request.parentId())
                    .orElseThrow(DepartmentErrorCode.DEPARTMENT_PARENT_NOT_FOUND::toException);
        }

        LocalDateTime now = LocalDateTime.now();
        Department department = Department.builder()
                .code(request.code())
                .name(request.name())
                .parentId(request.parentId())
                .level(request.level() != null ? request.level() : 1)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .status("ENABLED")
                .description(request.description())
                .createdAt(now)
                .updatedAt(now)
                .build();

        department = departmentRepository.save(department);
        return toResponse(department);
    }

    /**
     * 更新科室
     */
    @Transactional
    @CacheEvict(value = {"departmentTree", "departmentList"}, allEntries = true)
    public DepartmentResponse updateDepartment(Long id, DepartmentUpdateRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(DepartmentErrorCode.DEPARTMENT_NOT_FOUND::toException);

        // 校验父科室
        if (request.parentId() != null && !request.parentId().equals(id)) {
            departmentRepository.findById(request.parentId())
                    .orElseThrow(DepartmentErrorCode.DEPARTMENT_PARENT_NOT_FOUND::toException);
        }

        department.setName(request.name());
        department.setParentId(request.parentId());
        if (request.level() != null) {
            department.setLevel(request.level());
        }
        if (request.sortOrder() != null) {
            department.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            department.setStatus(request.status());
        }
        department.setDescription(request.description());
        department.setUpdatedAt(LocalDateTime.now());

        department = departmentRepository.save(department);
        return toResponse(department);
    }

    /**
     * 构建树形结构
     */
    private List<DepartmentResponse> buildTree(List<Department> departments) {
        Map<Long, List<Department>> byParent = departments.stream()
                .collect(Collectors.groupingBy(
                        dept -> dept.getParentId() == null ? 0L : dept.getParentId()));

        return buildChildren(byParent, 0L);
    }

    /**
     * 递归构建子节点
     */
    private List<DepartmentResponse> buildChildren(Map<Long, List<Department>> byParent, Long parentId) {
        List<Department> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }

        return children.stream()
                .sorted((a, b) -> Integer.compare(
                        a.getSortOrder() != null ? a.getSortOrder() : 0,
                        b.getSortOrder() != null ? b.getSortOrder() : 0))
                .map(dept -> new DepartmentResponse(
                        dept.getId(),
                        dept.getCode(),
                        dept.getName(),
                        dept.getParentId(),
                        dept.getLevel(),
                        dept.getSortOrder(),
                        dept.getStatus(),
                        dept.getDescription(),
                        dept.getCreatedAt(),
                        dept.getUpdatedAt(),
                        buildChildren(byParent, dept.getId())
                ))
                .collect(Collectors.toList());
    }

    /**
     * 转换为响应 DTO
     */
    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getParentId(),
                department.getLevel(),
                department.getSortOrder(),
                department.getStatus(),
                department.getDescription(),
                department.getCreatedAt(),
                department.getUpdatedAt(),
                null
        );
    }
}
