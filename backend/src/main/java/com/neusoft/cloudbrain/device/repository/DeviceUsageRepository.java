package com.neusoft.cloudbrain.device.repository;

import com.neusoft.cloudbrain.device.entity.DeviceUsage;
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
 * 设备使用记录 Repository
 */
@Repository
public interface DeviceUsageRepository extends JpaRepository<DeviceUsage, Long> {

    /**
     * 查询设备的使用记录（按开始时间倒序）
     */
    List<DeviceUsage> findByDeviceIdOrderByStartTimeDesc(Long deviceId);

    /**
     * 查询就诊的设备使用记录
     */
    List<DeviceUsage> findByEncounterId(Long encounterId);

    /**
     * 查询设备的当前使用中记录（状态为 IN_USAGE）
     */
    Optional<DeviceUsage> findFirstByDeviceIdAndStatusOrderByStartTimeDesc(Long deviceId, String status);

    /**
     * 查询操作人的使用记录（分页）
     */
    Page<DeviceUsage> findByUsedBy(Long usedBy, Pageable pageable);

    /**
     * 查询时间范围内的使用记录
     */
    @Query("SELECT u FROM DeviceUsage u WHERE u.deviceId = :deviceId " +
           "AND u.startTime BETWEEN :startTime AND :endTime " +
           "ORDER BY u.startTime DESC")
    List<DeviceUsage> findByDeviceIdAndTimeRange(
            @Param("deviceId") Long deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 更新使用记录状态（CAS 模式）
     */
    @Modifying
    @Query("UPDATE DeviceUsage u SET u.status = :newStatus, u.endTime = :endTime, " +
           "u.updatedAt = :now WHERE u.id = :usageId AND u.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("usageId") Long usageId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("endTime") LocalDateTime endTime,
            @Param("now") LocalDateTime now);
}
