package com.neusoft.cloudbrain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求
 *
 * 重置后 mustChangePassword=true（强制下次改密），并递增 tokenVersion 使旧 Token 失效。
 */
public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        String newPassword
) {
}
