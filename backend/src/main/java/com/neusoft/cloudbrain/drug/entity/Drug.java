package com.neusoft.cloudbrain.drug.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品实体
 */
@Entity
@Table(name = "drug", uniqueConstraints = {
        @UniqueConstraint(name = "uk_drug_code", columnNames = "code")
}, indexes = {
        @Index(name = "idx_drug_name", columnList = "name"),
        @Index(name = "idx_drug_category", columnList = "category"),
        @Index(name = "idx_drug_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "generic_name", length = 128)
    private String genericName;

    @Column(name = "dosage_form", nullable = false, length = 32)
    private String dosageForm;

    @Column(nullable = false, length = 32)
    private String strength;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "ENABLED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
