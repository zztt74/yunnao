package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 分诊请求
 *
 * 输入字段来自 13_AI能力集成AI任务书.md 第3.1节：
 * 年龄区间、性别、主诉、症状持续时间、补充描述。
 *
 * 不包含患者 ID、姓名、手机号等隐私信息（最小化原则）。
 */
public record TriageAIRequest(
        String ageRange,
        String gender,
        String chiefComplaint,
        String duration,
        String supplement) {
}
