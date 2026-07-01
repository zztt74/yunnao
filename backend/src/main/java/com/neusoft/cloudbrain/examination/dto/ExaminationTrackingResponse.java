package com.neusoft.cloudbrain.examination.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 患者检查流程追踪响应（UF-02）
 *
 * 面向患者端"检查检验流程追踪"页，展示全状态检查申请 + 流程引导信息。
 *
 * 与 ExaminationOrderResponse 的差异：
 * - 聚合 Doctor/Department 信息（医生名、科室名）
 * - 聚合 DeviceUsage 关联设备信息（设备名、位置）——按 encounterId 关联，非精确
 * - 派生 nextAction 文案（按状态）
 * - timeline 把时间戳集中展示
 *
 * 患者可见全状态申请（ORDERED/IN_PROGRESS/RESULT_ENTERED/REVIEWED/CANCELLED），
 * 但结果详情（resultText/conclusion 等）仍需 REVIEWED 才能查，走 /api/examinations/{id}/result。
 *
 * 设备关联策略（保守）：
 * - DeviceUsage 按 encounterId 关联，不直接关联 examinationOrderId
 * - 一个 encounter 多项检查 + 多次设备使用时无法精确匹配
 * - 因此 deviceName/deviceLocation 为该就诊下所有相关设备使用的列表，前端自行展示
 * - 检查类（LABORATORY）通常无设备使用记录，相关字段为 null
 */
public record ExaminationTrackingResponse(
        Long orderId,
        Long encounterId,
        String orderType,
        String itemCode,
        String itemName,
        String status,

        // 流程引导
        String doctorName,
        Long departmentId,
        String departmentName,
        String departmentLocation,
        String nextAction,

        // 设备关联（按 encounterId，非精确）
        String deviceName,
        String deviceLocation,

        // 时间线
        LocalDateTime orderedAt,
        LocalDateTime inProgressAt,
        LocalDateTime resultEnteredAt,
        LocalDateTime reviewedAt,
        LocalDateTime cancelledAt,
        String cancelReason) {

    /**
     * 按状态派生下一步动作文案
     */
    public static String deriveNextAction(String status, String departmentName) {
        if (status == null) return null;
        String dept = departmentName != null ? departmentName : "对应科室";
        return switch (status) {
            case "ORDERED" -> "医生已开立，请前往" + dept + "检查";
            case "IN_PROGRESS" -> "检查进行中";
            case "RESULT_ENTERED" -> "结果已录入，等待医生审核";
            case "REVIEWED" -> "已审核，可查看报告";
            case "CANCELLED" -> "已取消";
            default -> null;
        };
    }

    /**
     * 时间线列表（按时间顺序）
     */
    public List<LocalDateTime> timeline() {
        return List.of(orderedAt, inProgressAt, resultEnteredAt, reviewedAt, cancelledAt)
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
