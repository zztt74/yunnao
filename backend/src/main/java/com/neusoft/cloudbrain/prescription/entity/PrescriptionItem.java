package com.neusoft.cloudbrain.prescription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方明细实体
 *
 * 规则：
 * - 处方明细必须引用系统内固定虚构药品（drug_code 关联 drug.code）
 * - 剂量数值用于确定性规则校验
 */
@Entity
@Table(name = "prescription_item", indexes = {
        @Index(name = "idx_prescription_item_prescription_id", columnList = "prescription_id"),
        @Index(name = "idx_prescription_item_drug_code", columnList = "drug_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "drug_code", nullable = false, length = 32)
    private String drugCode;

    @Column(name = "drug_name", nullable = false, length = 128)
    private String drugName;

    @Column(nullable = false, length = 64)
    private String dosage;

    @Column(name = "dosage_value", precision = 10, scale = 3)
    private BigDecimal dosageValue;

    @Column(nullable = false, length = 32)
    private String frequency;

    @Column(nullable = false)
    private Integer duration;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(length = 512)
    private String instructions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
