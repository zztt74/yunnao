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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备实体
 *
 * 状态机（来自 12_业务流程与状态机.md 第10节）：
 * AVAILABLE → IN_USE        开始使用
 * IN_USE → AVAILABLE         使用结束
 * IN_USE → ABNORMAL          发现异常（必须先结束使用记录）
 * ABNORMAL → MAINTENANCE     送修（扩展版本）
 * ABNORMAL → AVAILABLE       修复完成
 * AVAILABLE → DISABLED       停用
 * DISABLED → AVAILABLE       重新启用
 */
@Entity
@Table(name = "device", indexes = {
        @Index(name = "uk_device_code", columnList = "code", unique = true),
        @Index(name = "idx_device_department_id", columnList = "department_id"),
        @Index(name = "idx_device_status", columnList = "status"),
        @Index(name = "idx_device_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "AVAILABLE";

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

    @Column(name = "last_maintenance")
    private LocalDateTime lastMaintenance;

    @Column(length = 128)
    private String location;

    @Column(length = 128)
    private String manufacturer;

    @Column(length = 64)
    private String model;

    @Column(name = "serial_number", length = 64)
    private String serialNumber;

    @Column(length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
