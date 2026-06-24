package com.neusoft.cloudbrain.schedule.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 排班模块错误码
 *
 * 错误码分类（来自 30_接口数据与错误契约.md 第7节）：
 * - SCHEDULE_*：排班错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 404 资源不存在
 * - 409 业务冲突
 */
@Getter
public enum ScheduleErrorCode {

    // 排班不存在 404
    SCHEDULE_NOT_FOUND("排班不存在", 404),

    // 排班状态冲突 409
    SCHEDULE_STATUS_CONFLICT("排班状态冲突", 409),
    SCHEDULE_CONFLICT("同一医生排班时间重叠", 409),
    SCHEDULE_CAPACITY_CONFLICT("已有预约数大于新的最大号源数", 409),
    SCHEDULE_CANCEL_CONFLICT("存在进行中或已完成的挂号，不能取消排班", 409),
    SCHEDULE_FULL("号源已满", 409),
    SCHEDULE_CANCELLED("排班已取消", 409),
    SCHEDULE_EXPIRED("排班已过期", 409),

    // 参数错误 400
    SCHEDULE_TIME_INVALID("开始时间必须早于结束时间", 400),

    // 依赖资源不存在 404
    DOCTOR_NOT_FOUND("医生不存在", 404),
    DOCTOR_DISABLED("医生已停用，不能创建排班", 409),
    DEPARTMENT_NOT_FOUND("科室不存在", 404),
    DEPARTMENT_DISABLED("科室已停用，不能创建排班", 409);

    private final String message;
    private final int httpStatus;

    ScheduleErrorCode(String message, int httpStatus) {
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
