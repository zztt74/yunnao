package com.neusoft.cloudbrain.drug.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品成分实体
 */
@Entity
@Table(name = "drug_ingredient", indexes = {
        @Index(name = "idx_drug_ingredient_drug_id", columnList = "drug_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrugIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_id", nullable = false)
    private Long drugId;

    @Column(name = "ingredient_name", nullable = false, length = 128)
    private String ingredientName;

    @Column(nullable = false, length = 32)
    private String amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
