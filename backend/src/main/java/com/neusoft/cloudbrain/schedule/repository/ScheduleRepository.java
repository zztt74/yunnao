package com.neusoft.cloudbrain.schedule.repository;

import com.neusoft.cloudbrain.schedule.entity.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 排班 Repository
 *
 * 并发控制策略（来自文档）：
 * 使用数据库条件更新防止超卖，条件至少包含"未取消、未过期、已预约数小于容量"。
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * 条件更新：扣减号源（防止超卖）
     *
     * 条件：未取消、未过期、已预约数小于容量
     * 返回受影响行数：1=成功，0=冲突（号源已满或排班不可用）
     */
    @Modifying
    @Query("UPDATE Schedule s SET s.bookedCount = s.bookedCount + 1, s.updatedAt = :now " +
           "WHERE s.id = :scheduleId " +
           "AND s.status NOT IN ('CANCELLED', 'COMPLETED') " +
           "AND s.cancelledAt IS NULL " +
           "AND s.bookedCount < s.maxAppointments " +
           "AND s.endTime > :now")
    int incrementBookedCount(@Param("scheduleId") Long scheduleId, @Param("now") LocalDateTime now);

    /**
     * 条件更新：恢复号源（取消挂号时）
     *
     * 条件：未取消、已预约数大于0
     * 返回受影响行数：1=成功，0=无需恢复
     */
    @Modifying
    @Query("UPDATE Schedule s SET s.bookedCount = s.bookedCount - 1, s.updatedAt = :now " +
           "WHERE s.id = :scheduleId " +
           "AND s.status NOT IN ('CANCELLED', 'COMPLETED') " +
           "AND s.bookedCount > 0")
    int decrementBookedCount(@Param("scheduleId") Long scheduleId, @Param("now") LocalDateTime now);

    /**
     * 按医生 ID 查询排班
     */
    List<Schedule> findByDoctorId(Long doctorId);

    /**
     * 按科室 ID 查询排班
     */
    List<Schedule> findByDepartmentId(Long departmentId);

    /**
     * 按日期查询排班
     */
    List<Schedule> findByScheduleDate(LocalDate scheduleDate);

    /**
     * 按医生 ID 和日期查询排班
     */
    List<Schedule> findByDoctorIdAndScheduleDate(Long doctorId, LocalDate scheduleDate);

    /**
     * 按状态查询排班
     */
    List<Schedule> findByStatus(String status);

    /**
     * 按医生 ID 和日期范围查询排班（检查冲突）
     */
    @Query("SELECT s FROM Schedule s WHERE s.doctorId = :doctorId " +
           "AND s.scheduleDate = :scheduleDate " +
           "AND s.status NOT IN ('CANCELLED') " +
           "AND s.startTime < :endTime " +
           "AND s.endTime > :startTime")
    List<Schedule> findConflictingSchedules(
            @Param("doctorId") Long doctorId,
            @Param("scheduleDate") LocalDate scheduleDate,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按科室 ID 和日期查询可预约排班（分页）
     */
    Page<Schedule> findByDepartmentIdAndScheduleDateAndStatusNot(
            Long departmentId, LocalDate scheduleDate, String status, Pageable pageable);

    /**
     * 按医生 ID 查询排班（分页）
     */
    Page<Schedule> findByDoctorId(Long doctorId, Pageable pageable);

    /**
     * 查询过期但未结束的排班（用于状态更新）
     */
    @Query("SELECT s FROM Schedule s WHERE s.status = 'AVAILABLE' AND s.endTime < :now")
    List<Schedule> findExpiredSchedules(@Param("now") LocalDateTime now);
}
