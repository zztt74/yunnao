package com.neusoft.cloudbrain.drug.dto;

import java.util.List;

/**
 * 药品相互作用响应
 */
public record DrugInteractionResponse(
        String drugACode,
        String drugBCode,
        String severity,
        String description
) {
}
