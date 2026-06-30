package com.neusoft.cloudbrain.triage.repository;

import com.neusoft.cloudbrain.triage.entity.TriageRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * 管理员全量分诊记录查询（多条件分页）
     *
     * @param patientId    患者筛选（可空）
     * @param priority     优先级筛选，对应 aiPriority（可空）
     * @param departmentId 映射科室筛选，对应 mappedDepartmentId（可空）
     * @param startDate    开始时间（可空，闭区间）
     * @param endDate      结束时间（可空，开区间）
     */
    @Query("SELECT t FROM TriageRecord t WHERE " +
            "(:patientId IS NULL OR t.patientId = :patientId) " +
            "AND (:priority IS NULL OR t.aiPriority = :priority) " +
            "AND (:departmentId IS NULL OR t.mappedDepartmentId = :departmentId) " +
            "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR t.createdAt < :endDate) " +
            "ORDER BY t.createdAt DESC")
    Page<TriageRecord> searchTriageRecords(
            @Param("patientId") Long patientId,
            @Param("priority") String priority,
            @Param("departmentId") Long departmentId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
