package com.neusoft.cloudbrain.appointment.repository;

import com.neusoft.cloudbrain.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
