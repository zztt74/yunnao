package com.neusoft.cloudbrain.patient.repository;

import com.neusoft.cloudbrain.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
