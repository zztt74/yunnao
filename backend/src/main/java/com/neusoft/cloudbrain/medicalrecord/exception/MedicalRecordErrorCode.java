package com.neusoft.cloudbrain.medicalrecord.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 病历模块错误码
 *
 * 错误码分类（来自 33_错误码与时间规范.md 第3节）：
 * - MEDICAL_RECORD_*：病历错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 403 权限不足
 * - 404 资源不存在
 * - 409 业务冲突
 */
@Getter
public enum MedicalRecordErrorCode {

    // 病历不存在 404
    MEDICAL_RECORD_NOT_FOUND("病历不存在", 404),

    // 依赖资源不存在 404
    ENCOUNTER_NOT_FOUND("就诊不存在", 404),
    PATIENT_NOT_FOUND("患者不存在", 404),
    DOCTOR_NOT_FOUND("医生不存在", 404),

    // 状态冲突 409
    MEDICAL_RECORD_STATUS_CONFLICT("病历状态冲突，不允许该状态转换", 409),
    MEDICAL_RECORD_ALREADY_CONFIRMED("病历已确认，基础版本不允许直接修改", 409),
    MEDICAL_RECORD_CONFIRMED_EXISTS("该就诊已存在已确认病历，基础版本每个就诊只能有一条当前有效的 CONFIRMED 记录", 409),
    MEDICAL_RECORD_NOT_CONFIRMED("病历未确认，不能完成就诊", 409),
    MEDICAL_RECORD_AI_SOURCE_VIOLATION("AI 只能生成 DRAFT 或 AI_GENERATED，CONFIRMED 必须由医生完成", 409),

    // 参数错误 400
    MEDICAL_RECORD_PARAM_INVALID("参数错误", 400),

    // 权限不足 403
    MEDICAL_RECORD_PERMISSION_DENIED("无权操作该病历，医生只能处理本人接诊就诊的病历", 403);

    private final String message;
    private final int httpStatus;

    MedicalRecordErrorCode(String message, int httpStatus) {
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
