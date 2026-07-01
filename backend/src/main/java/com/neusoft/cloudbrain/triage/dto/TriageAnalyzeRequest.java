package com.neusoft.cloudbrain.triage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 分诊分析请求
 *
 * 患者输入症状，请求 AI 分诊建议。
 *
 * 多轮扩展（UF-01）：
 * - conversationId：前端生成 UUID，同一会话多轮请求共用
 * - history：历史对话，role 仅 USER/ASSISTANT，content 只能是症状描述（禁止传隐私）
 * - round：第几轮，从 1 开始；首轮不传或传 1
 *
 * 单轮兼容：conversationId/history/round 均可选，老调用方式不受影响。
 */
public record TriageAnalyzeRequest(
        @NotNull(message = "患者 ID 不能为空")
        Long patientId,

        @NotBlank(message = "主诉症状不能为空")
        @Size(min = 2, max = 2000, message = "主诉内容长度需在 2-2000 字符之间")
        String symptoms,

        @Size(max = 32, message = "症状持续时间长度不能超过 32 字符")
        String duration,

        @Size(max = 2000, message = "补充信息长度不能超过 2000 字符")
        String supplement,

        // ===== UF-01 多轮扩展（可选，向后兼容）=====

        @Pattern(regexp = "^$|^[0-9a-fA-F-]{8,36}$", message = "conversationId 格式无效")
        String conversationId,

        List<ChatMessage> history,

        @Min(value = 1, message = "round 最小为 1")
        @Max(value = 20, message = "round 最大为 20，超过请重新发起问诊")
        Integer round) {

    /**
     * 单轮兼容构造器（老调用方式，UF-01 之前）
     */
    public TriageAnalyzeRequest(Long patientId, String symptoms, String duration, String supplement) {
        this(patientId, symptoms, duration, supplement, null, null, null);
    }
}
