package com.neusoft.cloudbrain.examination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 检查检验结果录入请求
 */
public record ExaminationResultRequest(
        @NotBlank(message = "结果文本不能为空")
        String resultText,

        @Size(max = 512, message = "参考范围长度不能超过 512 字符")
        String normalRange,

        @Size(max = 512, message = "结论长度不能超过 512 字符")
        String conclusion,

        String abnormalFlag) {
}
