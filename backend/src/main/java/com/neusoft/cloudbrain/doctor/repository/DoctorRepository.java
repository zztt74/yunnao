package com.neusoft.cloudbrain.doctor.repository;

import com.neusoft.cloudbrain.doctor.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 医生 Repository
 */
@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /**
     * 根据用户 ID 查询医生
     */
    Optional<Doctor> findByUserId(Long userId);

    /**
     * 根据科室 ID 查询医生
     */
    List<Doctor> findByDepartmentId(Long departmentId);

    /**
     * 根据状态查询医生
     */
    List<Doctor> findByStatus(String status);

    /**
     * 根据科室 ID 和状态查询医生
     */
    List<Doctor> findByDepartmentIdAndStatus(Long departmentId, String status);

    /**
     * 根据姓名模糊查询（分页）
     */
    Page<Doctor> findByNameContaining(String name, Pageable pageable);

    /**
     * 检查用户 ID 是否已关联医生
     */
    boolean existsByUserId(Long userId);
}
