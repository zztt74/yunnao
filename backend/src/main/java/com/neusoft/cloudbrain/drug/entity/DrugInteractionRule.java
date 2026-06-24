package com.neusoft.cloudbrain.drug.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品相互作用规则实体
 */
@Entity
@Table(name = "drug_interaction_rule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_drug_interaction_rule",
                columnNames = {"drug_a_code", "drug_b_code"})
}, indexes = {
        @Index(name = "idx_drug_interaction_rule_drug_a", columnList = "drug_a_code"),
        @Index(name = "idx_drug_interaction_rule_drug_b", columnList = "drug_b_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrugInteractionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_a_code", nullable = false, length = 32)
    private String drugACode;

    @Column(name = "drug_b_code", nullable = false, length = 32)
    private String drugBCode;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
