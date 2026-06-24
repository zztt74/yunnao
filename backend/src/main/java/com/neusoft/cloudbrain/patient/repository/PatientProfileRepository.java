package com.neusoft.cloudbrain.patient.repository;

import com.neusoft.cloudbrain.patient.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 患者扩展档案 Repository
 */
@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {

    /**
     * 根据患者 ID 查询档案
     */
    Optional<PatientProfile> findByPatientId(Long patientId);
}
