package com.neusoft.cloudbrain.appointment.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 挂号模块错误码
 *
 * 错误码分类（来自 30_接口数据与错误契约.md 第7节）：
 * - APPOINTMENT_*：挂号错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 403 权限不足
 * - 404 资源不存在
 * - 409 业务冲突
 */
@Getter
public enum AppointmentErrorCode {

    // 挂号不存在 404
    APPOINTMENT_NOT_FOUND("挂号不存在", 404),

    // 挂号状态冲突 409
    APPOINTMENT_STATUS_CONFLICT("挂号状态冲突", 409),
    APPOINTMENT_DUPLICATED("同一患者不能重复预约同一排班", 409),
    APPOINTMENT_SCHEDULE_FULL("号源已满", 409),
    APPOINTMENT_SCHEDULE_NOT_AVAILABLE("排班不可预约（已取消/已过期/已满）", 409),
    APPOINTMENT_CANNOT_CANCEL("当前挂号状态不能取消", 409),
    APPOINTMENT_SCHEDULE_CONFLICT("同一患者同一时段存在冲突挂号", 409),

    // 依赖资源不存在 404
    PATIENT_NOT_FOUND("患者不存在", 404),
    SCHEDULE_NOT_FOUND("排班不存在", 404),

    // 参数错误 400
    APPOINTMENT_PARAM_INVALID("参数错误", 400),

    // 权限不足 403
    APPOINTMENT_PERMISSION_DENIED("无权访问该挂号", 403);

    private final String message;
    private final int httpStatus;

    AppointmentErrorCode(String message, int httpStatus) {
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
