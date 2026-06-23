package com.neusoft.cloudbrain.drug.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 药品响应
 */
public record DrugResponse(
        Long id,
        String code,
        String name,
        String genericName,
        String dosageForm,
        String strength,
        String unit,
        String category,
        String status,
        List<IngredientResponse> ingredients,
        DosageRuleResponse dosageRule,
        List<ContraindicationResponse> contraindications,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record IngredientResponse(
            String ingredientName,
            String amount
    ) {
    }

    public record DosageRuleResponse(
            BigDecimal minDose,
            BigDecimal maxDose,
            BigDecimal maxSingleDose,
            String frequency
    ) {
    }

    public record ContraindicationResponse(
            String conditionType,
            String conditionValue,
            String description
    ) {
    }
}
