package com.neusoft.cloudbrain.triage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 分诊分析请求
 *
 * 患者输入症状，请求 AI 分诊建议。
 */
public record TriageAnalyzeRequest(
        @NotBlank(message = "患者 ID 不能为空")
        Long patientId,

        @NotBlank(message = "主诉症状不能为空")
        @Size(min = 2, max = 2000, message = "主诉内容长度需在 2-2000 字符之间")
        String symptoms,

        @Size(max = 32, message = "症状持续时间长度不能超过 32 字符")
        String duration,

        @Size(max = 2000, message = "补充信息长度不能超过 2000 字符")
        String supplement) {
}
