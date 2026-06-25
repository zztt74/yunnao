package com.neusoft.cloudbrain.examination.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 检查检验模块错误码
 *
 * 错误码分类（来自 33_错误码与时间规范.md 第3节）：
 * - EXAMINATION_*：检查业务错误
 * - LABORATORY_*：检验业务错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 403 权限不足
 * - 404 资源不存在
 * - 409 业务冲突
 */
@Getter
public enum ExaminationErrorCode {

    // 申请不存在 404
    EXAMINATION_ORDER_NOT_FOUND("检查检验申请不存在", 404),

    // 结果不存在 404
    EXAMINATION_RESULT_NOT_FOUND("检查检验结果不存在", 404),

    // 依赖资源不存在 404
    ENCOUNTER_NOT_FOUND("就诊不存在", 404),
    PATIENT_NOT_FOUND("患者不存在", 404),
    DOCTOR_NOT_FOUND("医生不存在", 404),

    // 状态冲突 409
    EXAMINATION_STATUS_CONFLICT("检查检验状态冲突，不允许该状态转换", 409),
    EXAMINATION_RESULT_ALREADY_EXISTS("该申请已存在结果记录，一个申请只能对应一条结果", 409),
    EXAMINATION_RESULT_NOT_REVIEWED("结果未审核，不可向患者展示", 409),
    EXAMINATION_RETURN_REASON_REQUIRED("退回重录必须记录原因", 409),

    // 参数错误 400
    EXAMINATION_PARAM_INVALID("参数错误", 400),
    EXAMINATION_ORDER_TYPE_INVALID("申请类型无效，只能为 EXAMINATION 或 LABORATORY", 400),

    // 权限不足 403
    EXAMINATION_PERMISSION_DENIED("无权操作该检查检验，医生只能处理本人接诊就诊的检查检验", 403);

    private final String message;
    private final int httpStatus;

    ExaminationErrorCode(String message, int httpStatus) {
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
