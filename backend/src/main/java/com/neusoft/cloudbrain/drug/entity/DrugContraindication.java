package com.neusoft.cloudbrain.drug.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品禁忌实体
 */
@Entity
@Table(name = "drug_contraindication", indexes = {
        @Index(name = "idx_drug_contraindication_drug_code", columnList = "drug_code"),
        @Index(name = "idx_drug_contraindication_type", columnList = "condition_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrugContraindication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_code", nullable = false, length = 32)
    private String drugCode;

    @Column(name = "condition_type", nullable = false, length = 32)
    private String conditionType;

    @Column(name = "condition_value", nullable = false, length = 128)
    private String conditionValue;

    @Column(length = 512)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
