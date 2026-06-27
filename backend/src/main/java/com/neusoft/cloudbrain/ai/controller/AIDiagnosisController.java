package com.neusoft.cloudbrain.ai.controller;

import com.neusoft.cloudbrain.ai.api.AIDiagnosisService;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIRequest;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIResult;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 辅助诊断 HTTP 接口
 *
 * 暴露 AIDiagnosisService 给前端调用，遵循诊断隔离原则：
 * - AI 只能产生候选建议（AI_SUGGESTION），不写入正式诊断
 * - AI 失败时返回 aiStatus=FAILED + 降级标记，医生可手工诊断
 *
 * 契约（来自 32_AI能力契约规范.md 第3节）：
 * - POST /api/ai/assist-diagnosis
 * - 请求：encounterId + 问诊上下文
 * - 响应：候选诊断列表 + aiStatus
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIDiagnosisController {

    private final AIDiagnosisService aiDiagnosisService;

    /**
     * AI 辅助诊断：根据问诊上下文生成候选诊断
     */
    @PostMapping("/assist-diagnosis")
    public AIAssistDiagnosisResponse assistDiagnosis(@RequestBody AIAssistDiagnosisRequest request) {
        log.info("AI 辅助诊断请求: encounterId={}", request.encounterId());

        try {
            // 映射为内部 DTO
            DiagnosisAIRequest internalRequest = new DiagnosisAIRequest(
                    request.chiefComplaint(),
                    request.presentIllness() != null ? request.presentIllness() : "",
                    request.pastHistory() != null ? request.pastHistory() : "",
                    request.physicalExam() != null ? request.physicalExam() : "",
                    "", // patientAgeRange - 前端暂不提供
                    ""  // patientGender - 前端暂不提供
            );

            DiagnosisAIResult result = aiDiagnosisService.analyze(internalRequest);

            // 映射为前端响应
            List<AICandidateDiagnosis> candidates = result.possibleDiagnoses().stream()
                    .map(d -> new AICandidateDiagnosis(
                            d.diagnosisCode(),
                            d.diagnosisName(),
                            d.explanation(),
                            confidenceToNumber(d.confidence()),
                            result.riskFactors(),
                            result.missingInformation(),
                            result.suggestedExaminations()))
                    .toList();

            return new AIAssistDiagnosisResponse(
                    request.encounterId(),
                    candidates,
                    "SUCCESS",
                    null);

        } catch (BusinessException e) {
            log.warn("AI 辅助诊断失败: encounterId={}, error={}",
                    request.encounterId(), e.getMessage());
            return new AIAssistDiagnosisResponse(
                    request.encounterId(),
                    List.of(),
                    "FAILED",
                    "AI_PROVIDER_UNAVAILABLE：" + e.getMessage() + "，请进行手工诊断。");

        } catch (Exception e) {
            log.error("AI 辅助诊断异常: encounterId={}", request.encounterId(), e);
            return new AIAssistDiagnosisResponse(
                    request.encounterId(),
                    List.of(),
                    "FAILED",
                    "AI_PROVIDER_UNAVAILABLE：AI 服务暂不可用，请进行手工诊断。");
        }
    }

    /**
     * 将置信度字符串转为前端需要的 0-1 数值
     */
    private double confidenceToNumber(String confidence) {
        return switch (confidence != null ? confidence : "LOW") {
            case "HIGH" -> 0.85;
            case "MEDIUM" -> 0.65;
            default -> 0.45;
        };
    }

    // ============================================================
    // 请求 / 响应 DTO
    // ============================================================

    /**
     * AI 辅助诊断请求（匹配前端 AiDiagnosisRequest）
     */
    public record AIAssistDiagnosisRequest(
            Long encounterId,
            String chiefComplaint,
            String presentIllness,
            String pastHistory,
            String physicalExam) {
    }

    /**
     * AI 辅助诊断响应（匹配前端 AiDiagnosisResponse）
     */
    public record AIAssistDiagnosisResponse(
            Long encounterId,
            List<AICandidateDiagnosis> candidates,
            String aiStatus,
            String aiFailureReason) {
    }

    /**
     * AI 候选诊断条目（匹配前端 AiCandidateDiagnosis）
     */
    public record AICandidateDiagnosis(
            String diagnosisCode,
            String diagnosisName,
            String reason,
            double confidence,
            List<String> riskFactors,
            List<String> informationGaps,
            List<String> recommendedExaminations) {
    }
}
