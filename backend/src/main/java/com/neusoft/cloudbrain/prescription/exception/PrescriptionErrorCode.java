package com.neusoft.cloudbrain.prescription.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 处方模块错误码
 *
 * 错误码分类（来自 33_错误码与时间规范.md 第3节）：
 * - PRESCRIPTION_*：处方业务错误
 *
 * HTTP 状态码使用规则：
 * - 400 参数错误
 * - 403 权限不足
 * - 404 资源不存在
 * - 409 业务冲突
 */
@Getter
public enum PrescriptionErrorCode {

    // 处方不存在 404
    PRESCRIPTION_NOT_FOUND("处方不存在", 404),

    // 依赖资源不存在 404
    ENCOUNTER_NOT_FOUND("就诊不存在", 404),
    PATIENT_NOT_FOUND("患者不存在", 404),
    DOCTOR_NOT_FOUND("医生不存在", 404),
    DRUG_NOT_FOUND("药品不存在于系统字典，必须使用系统内固定虚构药品", 404),

    // 状态冲突 409
    PRESCRIPTION_STATUS_CONFLICT("处方状态冲突，不允许该状态转换", 409),
    PRESCRIPTION_ALREADY_CONFIRMED("处方已确认，确认后只能作废", 409),
    PRESCRIPTION_VOIDED("处方已作废，不能继续操作", 409),
    PRESCRIPTION_CANNOT_VOID("当前处方状态不能作废，仅 CONFIRMED 状态可作废", 409),

    // 确定性规则违反 409
    PRESCRIPTION_RULE_VIOLATION("处方确定性规则校验未通过", 409),
    PRESCRIPTION_HIGH_RISK("处方高风险，需医生二次确认", 409),
    PRESCRIPTION_ALLERGY_CONTRAINDICATION("药物过敏禁忌，禁止开立", 409),
    PRESCRIPTION_INTERACTION_CONTRAINDICATION("严重药物相互作用禁忌，禁止开立", 409),

    // 参数错误 400
    PRESCRIPTION_PARAM_INVALID("参数错误", 400),
    PRESCRIPTION_ITEMS_EMPTY("药品明细不能为空，不得创建空处方", 400),

    // 权限不足 403
    PRESCRIPTION_PERMISSION_DENIED("无权操作该处方，医生只能处理本人接诊就诊的处方", 403);

    private final String message;
    private final int httpStatus;

    PrescriptionErrorCode(String message, int httpStatus) {
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
