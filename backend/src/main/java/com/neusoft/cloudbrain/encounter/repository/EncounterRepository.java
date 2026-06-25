package com.neusoft.cloudbrain.encounter.repository;

import com.neusoft.cloudbrain.encounter.entity.Encounter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 就诊 Repository
 */
@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    /**
     * 按挂号 ID 查询就诊（一个挂号最多一个就诊）
     */
    Optional<Encounter> findByAppointmentId(Long appointmentId);

    /**
     * 按患者 ID 查询就诊（分页）
     */
    Page<Encounter> findByPatientId(Long patientId, Pageable pageable);

    /**
     * 按医生 ID 查询就诊（分页）
     */
    Page<Encounter> findByDoctorId(Long doctorId, Pageable pageable);

    /**
     * 按医生 ID 和状态查询就诊
     */
    List<Encounter> findByDoctorIdAndStatus(Long doctorId, String status);

    /**
     * 按状态查询就诊
     */
    List<Encounter> findByStatus(String status);

    /**
     * 条件更新就诊状态（乐观锁）
     *
     * 返回受影响行数：1=成功，0=状态冲突
     */
    @Modifying
    @Query("UPDATE Encounter e SET e.status = :newStatus, e.updatedAt = :now " +
           "WHERE e.id = :encounterId AND e.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("encounterId") Long encounterId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("now") java.time.LocalDateTime now);
}
