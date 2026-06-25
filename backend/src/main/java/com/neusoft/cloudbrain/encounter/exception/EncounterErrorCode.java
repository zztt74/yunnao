package com.neusoft.cloudbrain.encounter.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 就诊模块错误码
 *
 * 错误码分类（来自 33_错误码与时间规范.md 第3节）：
 * - ENCOUNTER_*：就诊错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 403 权限不足
 * - 404 资源不存在
 * - 409 业务冲突
 */
@Getter
public enum EncounterErrorCode {

    // 就诊不存在 404
    ENCOUNTER_NOT_FOUND("就诊不存在", 404),

    // 依赖资源不存在 404
    APPOINTMENT_NOT_FOUND("挂号不存在", 404),
    PATIENT_NOT_FOUND("患者不存在", 404),
    DOCTOR_NOT_FOUND("医生不存在", 404),

    // 状态冲突 409
    ENCOUNTER_STATUS_CONFLICT("就诊状态冲突，不允许该状态转换", 409),
    ENCOUNTER_DUPLICATE("该挂号已存在就诊记录，一个挂号只能对应一个就诊", 409),
    ENCOUNTER_CANNOT_CANCEL("当前就诊状态不能取消", 409),

    // 完成就诊前置条件不满足 409
    ENCOUNTER_MEDICAL_RECORD_NOT_CONFIRMED("病历未确认，不能完成就诊", 409),
    ENCOUNTER_FINAL_DIAGNOSIS_REQUIRED("缺少医生最终诊断，不能完成就诊", 409),
    ENCOUNTER_EXAMINATION_PENDING("存在未完成或未审核的检查检验，不能完成就诊", 409),
    ENCOUNTER_PRESCRIPTION_PENDING("存在未确认或未作废的处方，不能完成就诊", 409),

    // 诊断隔离违规 409
    ENCOUNTER_DIAGNOSIS_ISOLATION_VIOLATION("AI 诊断只能创建 AI_SUGGESTION，不能创建 FINAL + DOCTOR 记录", 409),

    // 参数错误 400
    ENCOUNTER_PARAM_INVALID("参数错误", 400),

    // 权限不足 403
    ENCOUNTER_PERMISSION_DENIED("无权操作该就诊，医生只能处理本人接诊的就诊", 403);

    private final String message;
    private final int httpStatus;

    EncounterErrorCode(String message, int httpStatus) {
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
