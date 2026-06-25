package com.neusoft.cloudbrain.device.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 设备使用记录实体
 *
 * 使用状态：
 * IN_USAGE    使用中
 * COMPLETED   已完成（正常结束）
 * ABORTED     异常中止（设备异常导致）
 */
@Entity
@Table(name = "device_usage", indexes = {
        @Index(name = "idx_device_usage_device_id", columnList = "device_id"),
        @Index(name = "idx_device_usage_encounter_id", columnList = "encounter_id"),
        @Index(name = "idx_device_usage_used_by", columnList = "used_by"),
        @Index(name = "idx_device_usage_status", columnList = "status"),
        @Index(name = "idx_device_usage_start_time", columnList = "start_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "used_by", nullable = false)
    private Long usedBy;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "IN_USAGE";

    @Column(length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
