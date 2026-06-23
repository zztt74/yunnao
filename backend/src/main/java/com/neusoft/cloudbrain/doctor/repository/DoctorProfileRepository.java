package com.neusoft.cloudbrain.doctor.repository;

import com.neusoft.cloudbrain.doctor.entity.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 医生扩展档案 Repository
 */
@Repository
public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {

    /**
     * 根据医生 ID 查询档案
     */
    Optional<DoctorProfile> findByDoctorId(Long doctorId);
}
