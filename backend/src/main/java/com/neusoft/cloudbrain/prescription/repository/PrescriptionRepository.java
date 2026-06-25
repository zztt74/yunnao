package com.neusoft.cloudbrain.prescription.repository;

import com.neusoft.cloudbrain.prescription.entity.Prescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方 Repository
 */
@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /**
     * 按就诊 ID 查询处方
     */
    List<Prescription> findByEncounterId(Long encounterId);

    /**
     * 按患者 ID 查询处方（分页）
     */
    Page<Prescription> findByPatientId(Long patientId, Pageable pageable);

    /**
     * 按医生 ID 查询处方（分页）
     */
    Page<Prescription> findByDoctorId(Long doctorId, Pageable pageable);

    /**
     * 按就诊 ID 和状态查询处方
     */
    List<Prescription> findByEncounterIdAndStatus(Long encounterId, String status);

    /**
     * 统计就诊下未确认且未作废的处方数量（用于完成就诊前置条件校验）
     *
     * 处方可不存在；存在处方时，其业务状态必须为 CONFIRMED 或 VOIDED。
     * DRAFT 状态处方阻塞就诊完成。
     */
    @Query("SELECT COUNT(p) FROM Prescription p WHERE p.encounterId = :encounterId " +
           "AND p.status = 'DRAFT'")
    long countDraftByEncounterId(@Param("encounterId") Long encounterId);

    /**
     * 条件更新处方业务状态（乐观锁）
     *
     * 返回受影响行数：1=成功，0=状态冲突
     */
    @Modifying
    @Query("UPDATE Prescription p SET p.status = :newStatus, p.updatedAt = :now " +
           "WHERE p.id = :prescriptionId AND p.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("prescriptionId") Long prescriptionId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("now") LocalDateTime now);

    /**
     * 条件更新 AI 审核状态（乐观锁）
     */
    @Modifying
    @Query("UPDATE Prescription p SET p.aiReviewStatus = :newStatus, p.updatedAt = :now " +
           "WHERE p.id = :prescriptionId AND p.aiReviewStatus = :expectedStatus")
    int updateAIReviewStatusIfCurrent(
            @Param("prescriptionId") Long prescriptionId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("now") LocalDateTime now);
}
