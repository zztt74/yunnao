package com.neusoft.cloudbrain.examination.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查检验结果实体
 *
 * 规则（来自 12_业务流程与状态机.md 第10节 和 11_功能需求.md 第10节）：
 * - AI 解读不能修改原始数据
 * - 未审核结果不向患者展示
 * - REVIEWED 后不得直接覆盖原始结果
 * - 一个申请对应一条结果（uk_examination_result_order_id）
 *
 * AI 解读字段说明：
 * - ai_interpretation: AI 通俗解释（异步生成，不阻塞业务）
 * - ai_abnormal_items: AI 标记的异常项
 * - ai_follow_up_advice: AI 随访建议
 * - ai_status: AI 解读状态（NOT_REQUESTED/PENDING/SUCCESS/FAILED）
 * - AI 解读失败不影响业务，允许医生手工解读
 */
@Entity
@Table(name = "examination_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_examination_result_order_id", columnNames = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExaminationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "result_text", nullable = false, columnDefinition = "TEXT")
    private String resultText;

    @Column(name = "normal_range", length = 512)
    private String normalRange;

    @Column(length = 512)
    private String conclusion;

    @Column(name = "abnormal_flag", length = 16)
    private String abnormalFlag;

    @Column(name = "entered_by")
    private Long enteredBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "ai_interpretation", columnDefinition = "TEXT")
    private String aiInterpretation;

    @Column(name = "ai_abnormal_items", length = 512)
    private String aiAbnormalItems;

    @Column(name = "ai_follow_up_advice", columnDefinition = "TEXT")
    private String aiFollowUpAdvice;

    @Column(name = "ai_status", nullable = false, length = 16)
    @Builder.Default
    private String aiStatus = "NOT_REQUESTED";

    @Column(name = "ai_failure_reason", length = 255)
    private String aiFailureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
