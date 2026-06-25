package com.neusoft.cloudbrain.device.repository;

import com.neusoft.cloudbrain.device.entity.DeviceStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 设备状态变更历史 Repository
 */
@Repository
public interface DeviceStatusHistoryRepository extends JpaRepository<DeviceStatusHistory, Long> {

    /**
     * 查询设备的状态变更历史（按变更时间倒序）
     */
    List<DeviceStatusHistory> findByDeviceIdOrderByChangedAtDesc(Long deviceId);
}
