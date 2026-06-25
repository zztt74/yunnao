package com.neusoft.cloudbrain.medicalrecord.repository;

import com.neusoft.cloudbrain.medicalrecord.entity.MedicalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 病历 Repository
 */
@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    /**
     * 按就诊 ID 查询病历
     */
    List<MedicalRecord> findByEncounterId(Long encounterId);

    /**
     * 按患者 ID 查询病历（分页）
     */
    Page<MedicalRecord> findByPatientId(Long patientId, Pageable pageable);

    /**
     * 按就诊 ID 和状态查询病历列表
     */
    List<MedicalRecord> findByEncounterIdAndStatus(Long encounterId, String status);

    /**
     * 查询就诊的已确认病历（应仅有一条 CONFIRMED 记录）
     *
     * 基础版本每个 Encounter 只能有一条当前有效的 CONFIRMED 记录
     */
    Optional<MedicalRecord> findOneByEncounterIdAndStatus(Long encounterId, String status);

    /**
     * 检查就诊是否存在已确认病历
     */
    boolean existsByEncounterIdAndStatus(Long encounterId, String status);

    /**
     * 条件更新病历状态（乐观锁）
     *
     * 返回受影响行数：1=成功，0=状态冲突
     */
    @Modifying
    @Query("UPDATE MedicalRecord m SET m.status = :newStatus, " +
           "m.confirmedBy = :confirmedBy, m.confirmedAt = :confirmedAt, m.updatedAt = :now " +
           "WHERE m.id = :recordId AND m.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("recordId") Long recordId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("confirmedBy") Long confirmedBy,
            @Param("confirmedAt") LocalDateTime confirmedAt,
            @Param("now") LocalDateTime now);
}
