package com.neusoft.cloudbrain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求
 *
 * 重置后递增 tokenVersion 使旧 Token 失效。
 * 按 B-HW-03 要求，不再强制下次改密；用户可主动通过 /api/auth/change-password 修改。
 */
public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        String newPassword
) {
}
