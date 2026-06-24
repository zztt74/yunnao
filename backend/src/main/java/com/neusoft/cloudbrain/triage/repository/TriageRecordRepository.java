package com.neusoft.cloudbrain.triage.repository;

import com.neusoft.cloudbrain.triage.entity.TriageRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分诊记录 Repository
 */
@Repository
public interface TriageRecordRepository extends JpaRepository<TriageRecord, Long> {

    /**
     * 按患者 ID 查询分诊记录（分页）
     */
    Page<TriageRecord> findByPatientId(Long patientId, Pageable pageable);

    /**
     * 按患者 ID 查询分诊记录
     */
    List<TriageRecord> findByPatientId(Long patientId);
}
