package com.neusoft.cloudbrain.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 处方药品明细 DTO
 */
public record PrescriptionItemDTO(
        @NotBlank(message = "药品编码不能为空")
        String drugCode,

        @NotBlank(message = "药品名称不能为空")
        String drugName,

        @NotBlank(message = "剂量不能为空")
        String dosage,

        BigDecimal dosageValue,

        @NotBlank(message = "用药频次不能为空")
        String frequency,

        @NotNull(message = "疗程天数不能为空")
        @Positive(message = "疗程天数必须大于 0")
        Integer duration,

        @NotNull(message = "总数量不能为空")
        @Positive(message = "总数量必须大于 0")
        BigDecimal quantity,

        String instructions) {
}
