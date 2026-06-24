package com.neusoft.cloudbrain.appointment.repository;

import com.neusoft.cloudbrain.appointment.entity.Appointment;
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
 * 挂号 Repository
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * 按患者 ID 查询挂号
     */
    List<Appointment> findByPatientId(Long patientId);

    /**
     * 按患者 ID 查询挂号（分页）
     */
    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

    /**
     * 按医生 ID 查询挂号（分页）
     */
    Page<Appointment> findByDoctorId(Long doctorId, Pageable pageable);

    /**
     * 按排班 ID 查询挂号
     */
    List<Appointment> findByScheduleId(Long scheduleId);

    /**
     * 按医生 ID 查询挂号
     */
    List<Appointment> findByDoctorId(Long doctorId);

    /**
     * 按状态查询挂号
     */
    List<Appointment> findByStatus(String status);

    /**
     * 按患者 ID 和排班 ID 查询挂号（检查重复预约）
     */
    Optional<Appointment> findByPatientIdAndScheduleId(Long patientId, Long scheduleId);

    /**
     * 按医生 ID 和状态查询挂号
     */
    List<Appointment> findByDoctorIdAndStatus(Long doctorId, String status);

    /**
     * 按患者 ID 和状态查询挂号
     */
    List<Appointment> findByPatientIdAndStatus(Long patientId, String status);

    /**
     * 检查同一患者同一排班是否已有有效挂号（非取消）
     */
    boolean existsByPatientIdAndScheduleIdAndStatusNot(Long patientId, Long scheduleId, String status);

    /**
     * 条件更新挂号状态（用于就诊状态同步）
     *
     * 就诊状态变化时同步更新 Appointment 状态：
     * - 开始接诊：BOOKED/CHECKED_IN → IN_PROGRESS
     * - 等待检查：IN_PROGRESS → WAITING_EXAM
     * - 完成就诊：IN_PROGRESS → COMPLETED
     *
     * 返回受影响行数：1=成功，0=状态冲突
     */
    @Modifying
    @Query("UPDATE Appointment a SET a.status = :newStatus, a.updatedAt = :now " +
           "WHERE a.id = :appointmentId AND a.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("appointmentId") Long appointmentId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("now") LocalDateTime now);
}
