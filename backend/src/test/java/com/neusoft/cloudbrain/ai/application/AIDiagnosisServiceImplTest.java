package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIRequest;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AIDiagnosisServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-4 验收条件，6 个 Mock 场景 + source 隔离验证）：
 * - 正常：返回候选诊断列表（6 字段）
 * - 高风险（疑似急症）：HIGH 置信度 + disclaimer
 * - 空结果：信息不足返回空候选 + missingInformation 列出缺失内容（不编造诊断）
 * - 超时：Provider 超时降级为 BusinessException 504
 * - 非法 JSON：响应非法降级为 BusinessException 500
 * - 异常：Provider 异常降级为 BusinessException 504
 * - source 隔离验证：AI 输出不含 source=DOCTOR / type=FINAL 字段，仅产出候选诊断
 *
 * 输出 Schema（来自 13_AI能力集成AI任务书.md 第3.2节，6 个字段）：
 * possibleDiagnoses、evidence、missingInformation、riskFactors、suggestedExaminations、disclaimer
 *
 * 诊断隔离原则（来自 21_模块与依赖边界.md 第5.7节）：
 * - AI 原始结果只进 AIInvocation，不直接写业务表
 * - 候选诊断通过 EncounterDiagnosis(source=AI_SUGGESTION) 隔离
 * - AI 不创建 FINAL + DOCTOR 记录
 * - 不得写入医生正式诊断
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIDiagnosisServiceImpl - 辅助诊断服务测试")
class AIDiagnosisServiceImplTest {

    @Mock
    private AIInvocationRecorder recorder;

    @Mock
    private PromptManager promptManager;

    private AIDiagnosisServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonSchemaParser parser = new JsonSchemaParser(new ObjectMapper());
        service = new AIDiagnosisServiceImpl(recorder, parser, promptManager);
        when(promptManager.getPrompt(anyString())).thenReturn("system prompt");
        when(promptManager.getPromptVersion(anyString())).thenReturn("v1");
    }

    @Test
    @DisplayName("正常调用：胸痛返回候选诊断列表（6 字段）")
    void analyze_normal_returnsDiagnoses() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "胸痛", "无既往史", null, null, "40-50", "MALE");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            String json = "{\"possibleDiagnoses\":[{\"diagnosisCode\":\"I20.9\","
                    + "\"diagnosisName\":\"心绞痛\",\"confidence\":\"MEDIUM\","
                    + "\"explanation\":\"胸痛症状提示心肌缺血可能\"}],"
                    + "\"evidence\":[\"主诉胸痛\"],"
                    + "\"missingInformation\":[\"既往病史\",\"用药史\"],"
                    + "\"riskFactors\":[\"年龄\"],"
                    + "\"suggestedExaminations\":[\"心电图\",\"心肌酶谱\"],"
                    + "\"disclaimer\":\"本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        DiagnosisAIResult result = service.analyze(request);

        assertThat(result.possibleDiagnoses()).hasSize(1);
        assertThat(result.possibleDiagnoses().get(0).diagnosisCode()).isEqualTo("I20.9");
        assertThat(result.possibleDiagnoses().get(0).confidence()).isEqualTo("MEDIUM");
        assertThat(result.evidence()).contains("主诉胸痛");
        assertThat(result.missingInformation()).contains("既往病史");
        assertThat(result.riskFactors()).contains("年龄");
        assertThat(result.suggestedExaminations()).contains("心电图");
        assertThat(result.disclaimer()).contains("仅供医生参考");
    }

    @Test
    @DisplayName("高风险（疑似急症）：HIGH 置信度 + disclaimer")
    void analyze_highRisk_returnsHighConfidenceWithDisclaimer() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "胸痛伴大汗", "持续不缓解", "高血压史", null, "50-60", "MALE");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            String json = "{\"possibleDiagnoses\":[{\"diagnosisCode\":\"I21.9\","
                    + "\"diagnosisName\":\"急性心肌梗死\",\"confidence\":\"HIGH\","
                    + "\"explanation\":\"胸痛持续不缓解，高度提示心肌梗死\"}],"
                    + "\"evidence\":[\"胸痛持续不缓解\",\"可能伴随冷汗\"],"
                    + "\"missingInformation\":[\"心电图结果\",\"心肌酶谱结果\"],"
                    + "\"riskFactors\":[\"年龄\",\"高血压史\"],"
                    + "\"suggestedExaminations\":[\"心电图\",\"心肌酶谱\",\"肌钙蛋白\"],"
                    + "\"disclaimer\":\"本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        DiagnosisAIResult result = service.analyze(request);

        // 高风险建议带 disclaimer（验收条件）
        assertThat(result.disclaimer()).contains("仅供医生参考");
        // 高危症状标注 HIGH 置信度
        assertThat(result.possibleDiagnoses()).hasSize(1);
        assertThat(result.possibleDiagnoses().get(0).confidence()).isEqualTo("HIGH");
        assertThat(result.possibleDiagnoses().get(0).diagnosisName()).contains("心肌梗死");
        // 建议紧急检查
        assertThat(result.suggestedExaminations()).contains("心电图", "心肌酶谱");
    }

    @Test
    @DisplayName("空结果：信息不足返回空候选 + missingInformation 列出缺失内容（不编造诊断）")
    void analyze_emptyInput_returnsEmptyDiagnosesWithMissingInfo() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                null, null, null, null, null, null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            // 空结果：possibleDiagnoses 为空数组，missingInformation 列出缺失内容，不编造诊断
            String json = "{\"possibleDiagnoses\":[],"
                    + "\"evidence\":[],"
                    + "\"missingInformation\":[\"主诉缺失\",\"现病史缺失\",\"既往史缺失\"],"
                    + "\"riskFactors\":[],"
                    + "\"suggestedExaminations\":[\"血常规\",\"尿常规\"],"
                    + "\"disclaimer\":\"本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        DiagnosisAIResult result = service.analyze(request);

        // 空输入不编造诊断
        assertThat(result.possibleDiagnoses()).isEmpty();
        // 缺失信息在 missingInformation 列出（验收条件）
        assertThat(result.missingInformation()).isNotEmpty();
        assertThat(result.missingInformation()).contains("主诉缺失");
        assertThat(result.disclaimer()).isNotEmpty();
    }

    @Test
    @DisplayName("超时：Provider 超时降级为 BusinessException 504")
    void analyze_timeout_throwsBusinessException504() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "头痛", null, null, null, "30-40", "FEMALE");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("Mock 超时", true, null));

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_DIAGNOSIS_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 JSON：响应非法降级为 BusinessException 500")
    void analyze_invalidJson_throwsBusinessException500() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "头痛", null, null, null, "30-40", "FEMALE");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIInvalidResponseException("AI 响应非合法 JSON"));

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_DIAGNOSIS_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }

    @Test
    @DisplayName("异常：Provider 异常降级为 BusinessException 504")
    void analyze_providerError_throwsBusinessException504() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "头痛", null, null, null, "30-40", "FEMALE");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("500 错误", false, 500));

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_DIAGNOSIS_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 confidence 枚举值降级为 BusinessException")
    void analyze_invalidConfidence_throwsBusinessException() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "发热", null, null, null, "20-30", "MALE");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            // confidence=INVALID 非 HIGH/MEDIUM/LOW 枚举
            String json = "{\"possibleDiagnoses\":[{\"diagnosisCode\":\"J06.9\","
                    + "\"diagnosisName\":\"感冒\",\"confidence\":\"INVALID\","
                    + "\"explanation\":\"\"}],\"evidence\":[],"
                    + "\"missingInformation\":[],\"riskFactors\":[],"
                    + "\"suggestedExaminations\":[],"
                    + "\"disclaimer\":\"本结果由 AI 辅助生成\"}";
            // parser.apply 抛出 AIInvalidResponseException
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_DIAGNOSIS_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }

    @Test
    @DisplayName("source 隔离：AI 输出仅候选诊断，不包含 source=DOCTOR / type=FINAL 字段")
    void analyze_sourceIsolation_aiOutputDoesNotContainDoctorOrFinal() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "胸痛", "无", null, null, "40-50", "MALE");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            // AI 输出 Schema 仅 6 字段，不含 source / type / confirmedAt 等业务字段
            String json = "{\"possibleDiagnoses\":[{\"diagnosisCode\":\"I20.9\","
                    + "\"diagnosisName\":\"心绞痛\",\"confidence\":\"MEDIUM\","
                    + "\"explanation\":\"胸痛\"}],\"evidence\":[\"主诉\"],"
                    + "\"missingInformation\":[],\"riskFactors\":[],"
                    + "\"suggestedExaminations\":[\"心电图\"],"
                    + "\"disclaimer\":\"本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        DiagnosisAIResult result = service.analyze(request);

        // AI 输出 Schema 仅 6 字段，不包含 source / type / confirmedAt 等业务字段
        // DiagnosisAIResult record 的组件即为 6 字段，无 source/type 访问器
        assertThat(result).hasFieldOrProperty("possibleDiagnoses");
        assertThat(result).hasFieldOrProperty("evidence");
        assertThat(result).hasFieldOrProperty("missingInformation");
        assertThat(result).hasFieldOrProperty("riskFactors");
        assertThat(result).hasFieldOrProperty("suggestedExaminations");
        assertThat(result).hasFieldOrProperty("disclaimer");
        // AI 结果不包含医生确认相关字段（source=DOCTOR / type=FINAL 由 encounter 业务模块强制隔离）
        assertThat(result).extracting("possibleDiagnoses").asList().isNotEmpty();
        assertThat(result.possibleDiagnoses().get(0))
                .hasFieldOrProperty("diagnosisCode")
                .hasFieldOrProperty("diagnosisName")
                .hasFieldOrProperty("confidence")
                .hasFieldOrProperty("explanation");
        // PossibleDiagnosis 不含 source / type / confirmedAt（仅 4 字段：诊断编码、名称、置信度、说明）
        assertThat(result.possibleDiagnoses().get(0).getClass().getRecordComponents())
                .hasSize(4);
    }

    @Test
    @DisplayName("缺失必填字段 disclaimer 抛出 BusinessException")
    void analyze_missingDisclaimer_throwsBusinessException() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "头痛", null, null, null, "30-40", "FEMALE");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            // 缺少 disclaimer 字段，validateRequired 抛出 AIInvalidResponseException
            String invalidJson = "{\"possibleDiagnoses\":[{\"diagnosisCode\":\"G44.1\","
                    + "\"diagnosisName\":\"血管性头痛\",\"confidence\":\"MEDIUM\","
                    + "\"explanation\":\"\"}],\"evidence\":[\"主诉\"]}";
            // parser.apply 抛出异常，InvokeResult 构造不会执行
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(invalidJson), true, 1L);
        });

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_DIAGNOSIS_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }
}
