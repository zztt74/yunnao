package com.neusoft.cloudbrain.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 就诊诊断请求
 *
 * 诊断隔离原则（来自 12_业务流程与状态机.md 第7节）：
 * - AI 只能产生 AI_SUGGESTION（type=PRELIMINARY）
 * - 医生最终诊断必须为 type=FINAL、source=DOCTOR
 * - AI 不得创建 FINAL + DOCTOR 记录
 */
public record EncounterDiagnosisRequest(
        @NotBlank(message = "诊断编码不能为空")
        @Size(max = 32, message = "诊断编码长度不能超过 32 字符")
        String diagnosisCode,

        @NotBlank(message = "诊断名称不能为空")
        @Size(max = 128, message = "诊断名称长度不能超过 128 字符")
        String diagnosisName,

        @NotBlank(message = "诊断类型不能为空")
        String type,

        @NotBlank(message = "诊断来源不能为空")
        String source,

        @Size(max = 2000, message = "备注长度不能超过 2000 字符")
        String notes) {
}
