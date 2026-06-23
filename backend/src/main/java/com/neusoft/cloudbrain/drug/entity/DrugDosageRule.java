package com.neusoft.cloudbrain.drug.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品剂量规则实体
 */
@Entity
@Table(name = "drug_dosage_rule", indexes = {
        @Index(name = "idx_drug_dosage_rule_drug_code", columnList = "drug_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrugDosageRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_code", nullable = false, length = 32)
    private String drugCode;

    @Column(name = "min_dose", nullable = false, precision = 10, scale = 3)
    private BigDecimal minDose;

    @Column(name = "max_dose", nullable = false, precision = 10, scale = 3)
    private BigDecimal maxDose;

    @Column(name = "max_single_dose", nullable = false, precision = 10, scale = 3)
    private BigDecimal maxSingleDose;

    @Column(nullable = false, length = 32)
    private String frequency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
