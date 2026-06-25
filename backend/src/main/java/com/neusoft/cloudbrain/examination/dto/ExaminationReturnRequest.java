package com.neusoft.cloudbrain.examination.dto;

import jakarta.validation.constraints.Size;

/**
 * 检查检验退回重录请求
 *
 * RESULT_ENTERED → IN_PROGRESS 仅用于审核退回，必须记录原因
 */
public record ExaminationReturnRequest(
        @Size(max = 255, message = "退回原因长度不能超过 255 字符")
        String reason) {
}
