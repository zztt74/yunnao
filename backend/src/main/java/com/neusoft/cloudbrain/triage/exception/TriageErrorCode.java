package com.neusoft.cloudbrain.triage.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 分诊模块错误码
 *
 * 错误码分类（来自 33_错误码与时间规范.md 第3节）：
 * - TRIAGE_*：分诊错误
 * - AI_*：AI 调用错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 403 权限不足
 * - 404 资源不存在
 * - 409 业务冲突
 * - 504 上游超时
 */
@Getter
public enum TriageErrorCode {

    // 分诊记录不存在 404
    TRIAGE_NOT_FOUND("分诊记录不存在", 404),

    // 依赖资源不存在 404
    PATIENT_NOT_FOUND("患者不存在", 404),
    DEPARTMENT_NOT_FOUND("科室不存在", 404),

    // 参数错误 400
    TRIAGE_PARAM_INVALID("参数错误：主诉不能为空", 400),
    TRIAGE_SYMPTOMS_TOO_SHORT("主诉内容过短，请详细描述症状", 400),

    // 科室映射失败 404
    TRIAGE_DEPARTMENT_MAPPING_FAILED("AI 推荐科室不存在，请手动选择科室", 404),

    // 权限不足 403
    TRIAGE_PERMISSION_DENIED("无权访问该分诊记录", 403);

    private final String message;
    private final int httpStatus;

    TriageErrorCode(String message, int httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 抛出业务异常
     */
    public BusinessException toException() {
        return new BusinessException(name(), message, httpStatus);
    }
}
