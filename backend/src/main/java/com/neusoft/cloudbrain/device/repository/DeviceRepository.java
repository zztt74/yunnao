package com.neusoft.cloudbrain.device.repository;

import com.neusoft.cloudbrain.device.entity.Device;
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
 * 设备 Repository
 *
 * 提供并发控制：
 * - updateStatusIfCurrent：条件更新（CAS 模式），用于并发场景下的状态流转
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByCode(String code);

    List<Device> findByStatus(String status);

    List<Device> findByDepartmentId(Long departmentId);

    List<Device> findByType(String type);

    List<Device> findByDepartmentIdAndStatus(Long departmentId, String status);

    Page<Device> findByDepartmentId(Long departmentId, Pageable pageable);

    /**
     * 条件更新设备状态（CAS 模式，并发控制）
     *
     * @param deviceId       设备 ID
     * @param expectedStatus 期望的当前状态
     * @param newStatus      新状态
     * @param now            当前时间
     * @return 更新行数（0 表示状态已被其他事务修改，需抛出并发冲突异常）
     */
    @Modifying
    @Query("UPDATE Device d SET d.status = :newStatus, d.updatedAt = :now " +
           "WHERE d.id = :deviceId AND d.status = :expectedStatus")
    int updateStatusIfCurrent(
            @Param("deviceId") Long deviceId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("now") java.time.LocalDateTime now);

    /**
     * 关键字搜索（名称或编码）
     */
    @Query("SELECT d FROM Device d WHERE " +
           "(:keyword IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(d.code) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR d.status = :status) " +
           "AND (:type IS NULL OR d.type = :type) " +
           "AND (:departmentId IS NULL OR d.departmentId = :departmentId)")
    Page<Device> searchDevices(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("type") String type,
            @Param("departmentId") Long departmentId,
            Pageable pageable);
}
