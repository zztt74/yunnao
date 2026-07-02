package com.neusoft.cloudbrain.department.service;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.dto.DepartmentCreateRequest;
import com.neusoft.cloudbrain.department.dto.DepartmentResponse;
import com.neusoft.cloudbrain.department.dto.DepartmentUpdateRequest;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DepartmentService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DepartmentService - 科室服务测试")
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private Department testDepartment;

    @BeforeEach
    void setUp() {
        testDepartment = Department.builder()
                .id(1L)
                .code("DEPT_INTERNAL")
                .name("内科")
                .parentId(null)
                .level(1)
                .sortOrder(1)
                .status("ENABLED")
                .description("内科系统")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("获取科室树形结构 - 应返回层级结构")
    void getDepartmentTree_shouldReturnTree() {
        Department child = Department.builder()
                .id(2L)
                .code("DEPT_CARDIOLOGY")
                .name("心血管内科")
                .parentId(1L)
                .level(2)
                .sortOrder(1)
                .status("ENABLED")
                .build();

        when(departmentRepository.findAll()).thenReturn(List.of(testDepartment, child));

        List<DepartmentResponse> tree = departmentService.getDepartmentTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).name()).isEqualTo("内科");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).name()).isEqualTo("心血管内科");
    }

    @Test
    @DisplayName("获取科室详情 - 存在时应返回科室信息")
    void getDepartmentById_shouldReturnWhenExists() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        DepartmentResponse response = departmentService.getDepartmentById(1L);

        assertThat(response.code()).isEqualTo("DEPT_INTERNAL");
        assertThat(response.name()).isEqualTo("内科");
    }

    @Test
    @DisplayName("获取科室详情 - 不存在时应抛出异常")
    void getDepartmentById_shouldThrowWhenNotExists() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getDepartmentById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DEPARTMENT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("创建科室 - 编码唯一时应创建成功")
    void createDepartment_shouldCreateWhenCodeUnique() {
        DepartmentCreateRequest request = new DepartmentCreateRequest(
                "DEPT_NEW", "新科室", null, 1, 1, "描述");

        when(departmentRepository.existsByCode("DEPT_NEW")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenReturn(testDepartment);

        DepartmentResponse response = departmentService.createDepartment(request);

        assertThat(response).isNotNull();
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    @DisplayName("创建科室 - 编码重复时应抛出异常")
    void createDepartment_shouldThrowWhenCodeDuplicated() {
        DepartmentCreateRequest request = new DepartmentCreateRequest(
                "DEPT_INTERNAL", "内科", null, 1, 1, "描述");

        when(departmentRepository.existsByCode("DEPT_INTERNAL")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DEPARTMENT_CODE_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("更新科室 - 存在时应更新成功")
    void updateDepartment_shouldUpdateWhenExists() {
        DepartmentUpdateRequest request = new DepartmentUpdateRequest(
                "更新科室", null, 1, 1, "ENABLED", "更新描述");

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(departmentRepository.save(any(Department.class))).thenReturn(testDepartment);

        DepartmentResponse response = departmentService.updateDepartment(1L, request);

        assertThat(response).isNotNull();
        verify(departmentRepository).save(any(Department.class));
    }
}
