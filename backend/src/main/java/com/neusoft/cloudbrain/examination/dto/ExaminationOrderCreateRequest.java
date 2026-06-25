package com.neusoft.cloudbrain.examination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 检查检验申请创建请求
 */
public record ExaminationOrderCreateRequest(
        @NotNull(message = "就诊 ID 不能为空")
        Long encounterId,

        @NotBlank(message = "申请类型不能为空")
        String orderType,

        @Size(max = 64, message = "项目编码长度不能超过 64 字符")
        String itemCode,

        @NotBlank(message = "项目名称不能为空")
        @Size(max = 128, message = "项目名称长度不能超过 128 字符")
        String itemName) {
}
