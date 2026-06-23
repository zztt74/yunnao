package com.neusoft.cloudbrain.department.repository;

import com.neusoft.cloudbrain.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 科室 Repository
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * 根据编码查询科室
     */
    Optional<Department> findByCode(String code);

    /**
     * 根据父科室 ID 查询子科室
     */
    List<Department> findByParentId(Long parentId);

    /**
     * 根据状态查询科室
     */
    List<Department> findByStatus(String status);

    /**
     * 检查编码是否存在
     */
    boolean existsByCode(String code);
}
