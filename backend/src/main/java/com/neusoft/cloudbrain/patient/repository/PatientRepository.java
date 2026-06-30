package com.neusoft.cloudbrain.patient.repository;

import com.neusoft.cloudbrain.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 患者 Repository
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /**
     * 根据用户 ID 查询患者
     */
    Optional<Patient> findByUserId(Long userId);

    /**
     * 根据姓名查询患者
     */
    List<Patient> findByName(String name);

    /**
     * 根据手机号查询患者
     */
    List<Patient> findByPhone(String phone);

    /**
     * 根据状态查询患者
     */
    List<Patient> findByStatus(String status);

    /**
     * 检查用户 ID 是否已关联患者
     */
    boolean existsByUserId(Long userId);

    /**
     * 管理员患者分页查询（多条件）
     *
     * @param name   姓名模糊筛选（可空）
     * @param phone  手机号精确筛选（可空）
     * @param status 状态筛选（可空）
     */
    @Query("SELECT p FROM Patient p WHERE " +
            "(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND (:phone IS NULL OR p.phone = :phone) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "ORDER BY p.createdAt DESC")
    Page<Patient> searchPatients(
            @Param("name") String name,
            @Param("phone") String phone,
            @Param("status") String status,
            Pageable pageable);
}
