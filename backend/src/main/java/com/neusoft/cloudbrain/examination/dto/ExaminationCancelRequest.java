package com.neusoft.cloudbrain.examination.dto;

import jakarta.validation.constraints.Size;

/**
 * 检查检验取消请求
 */
public record ExaminationCancelRequest(
        @Size(max = 255, message = "取消原因长度不能超过 255 字符")
        String reason) {
}
