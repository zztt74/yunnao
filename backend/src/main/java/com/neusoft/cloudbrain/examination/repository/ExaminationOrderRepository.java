package com.neusoft.cloudbrain.examination.repository;

import com.neusoft.cloudbrain.examination.entity.ExaminationOrder;
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
 * 检查检验申请 Repository
 */
@Repository
public interface ExaminationOrderRepository extends JpaRepository<ExaminationOrder, Long> {

    /**
     * 按就诊 ID 查询申请
     */
    List<ExaminationOrder> findByEncounterId(Long encounterId);

    /**
     * 按患者 ID 查询申请（分页）
     */
    Page<ExaminationOrder> findByPatientId(Long patientId, Pageable pageable);

    /**
     * 按医生 ID 查询申请（分页）
     */
    Page<ExaminationOrder> findByDoctorId(Long doctorId, Pageable pageable);

    /**
     * 按就诊 ID 和状态查询申请
     */
    List<ExaminationOrder> findByEncounterIdAndStatus(Long encounterId, String status);

    /**
     * 检查就诊是否存在未完成或未审核的检查检验
     * 任一检查检验处于 ORDERED、IN_PROGRESS 或 RESULT_ENTERED 时不得完成就诊
     */
    @Query("SELECT COUNT(e) FROM ExaminationOrder e WHERE e.encounterId = :encounterId " +
           "AND e.status IN ('ORDERED', 'IN_PROGRESS', 'RESULT_ENTERED')")
    long countPendingByEncounterId(@Param("encounterId") Long encounterId);

    /**
     * 条件更新申请状态（乐观锁）
     *
     * 返回受影响行数：1=成功，0=状态冲突
     */
    @Modifying
    @Query("UPDATE ExaminationOrder e SET e.status = :newStatus, e.updatedAt = :now " +
           "WHERE e.id = :orderId AND e.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("orderId") Long orderId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("now") LocalDateTime now);
}
