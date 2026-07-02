package com.neusoft.cloudbrain.device.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 设备模块错误码
 *
 * 错误码分类（来自 33_错误码与时间规范.md 第3节）：
 * - DEVICE_*：设备业务错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 404 资源不存在
 * - 409 业务冲突（状态冲突、并发冲突）
 */
@Getter
public enum DeviceErrorCode {

    // 设备不存在 404
    DEVICE_NOT_FOUND("设备不存在", 404),
    DEVICE_USAGE_NOT_FOUND("设备使用记录不存在", 404),

    // 编码冲突 409
    DEVICE_CODE_DUPLICATED("设备编码已存在", 409),

    // 科室不存在 404
    DEVICE_DEPARTMENT_NOT_FOUND("所选科室不存在", 404),

    // 状态冲突 409
    DEVICE_STATUS_CONFLICT("设备状态冲突，不允许该状态转换", 409),
    DEVICE_NOT_AVAILABLE("设备当前不可用", 409),
    DEVICE_IN_USE("设备已被占用", 409),
    DEVICE_ABNORMAL("设备处于异常状态，不能使用", 409),
    DEVICE_DISABLED("设备已停用", 409),
    DEVICE_MAINTENANCE("设备维护中", 409),
    DEVICE_NO_ACTIVE_USAGE("设备当前没有进行中的使用记录", 409),
    DEVICE_USAGE_ALREADY_COMPLETED("设备使用记录已结束", 409),

    // 并发冲突 409
    DEVICE_CONCURRENT_CONFLICT("设备已被其他人占用，请刷新后重试", 409),

    // 参数错误 400
    DEVICE_PARAM_INVALID("参数错误", 400),
    DEVICE_INVALID_STATUS_TRANSITION("非法的状态转换", 400);

    private final String message;
    private final int httpStatus;

    DeviceErrorCode(String message, int httpStatus) {
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
