package com.neusoft.cloudbrain.triage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 对话消息（多轮分诊用）
 *
 * 仅用于分诊会话上下文，content 只能是症状/主诉描述，禁止传姓名、手机号等隐私。
 */
public record ChatMessage(
        @NotBlank(message = "role 不能为空")
        @Pattern(regexp = "USER|ASSISTANT", message = "role 只能为 USER 或 ASSISTANT")
        String role,

        @NotBlank(message = "content 不能为空")
        @Size(max = 2000, message = "content 长度不能超过 2000 字符")
        String content) {
}
