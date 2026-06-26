package com.neusoft.cloudbrain.ai.provider;

import com.neusoft.cloudbrain.ai.config.AIProperties;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MockAIProvider 单元测试
 *
 * 覆盖七场景分流（来自 32_AI能力契约规范.md 第4节）：
 * - 正常 / 高风险 / 空 / 超时 / 非法JSON / 不存在科室 / 异常
 *
 * 覆盖五个能力的 Mock 响应格式正确性
 */
@DisplayName("MockAIProvider - 七场景分流测试")
class MockAIProviderTest {

    private MockAIProvider provider;

    @BeforeEach
    void setUp() {
        AIProperties properties = new AIProperties();
        provider = new MockAIProvider(properties);
    }

    // ============================================================
    // 七场景分流测试
    // ============================================================

    @Test
    @DisplayName("正常场景：分诊胸痛返回心血管内科 HIGH")
    void normalScenario_triage_chestPain() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "主诉: 胸痛; 持续时间: 2小时"));

        assertThat(response.mock()).isTrue();
        assertThat(response.content()).contains("DEPT_CARDIOLOGY");
        assertThat(response.content()).contains("\"HIGH\"");
        assertThat(response.content()).contains("\"emergencySuggested\":true");
    }

    @Test
    @DisplayName("正常场景：分诊头痛返回神经内科")
    void normalScenario_triage_headache() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "主诉: 头痛"));

        assertThat(response.content()).contains("DEPT_NEUROLOGY");
        assertThat(response.content()).contains("\"MEDIUM\"");
    }

    @Test
    @DisplayName("高风险场景：MOCK_HIGH_RISK 关键词触发高风险结果")
    void highRiskScenario() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("diagnosis", "MOCK_HIGH_RISK 胸痛"));

        assertThat(response.mock()).isTrue();
        assertThat(response.model()).isEqualTo("mock-high-risk");
        assertThat(response.content()).contains("\"HIGH\"");
        assertThat(response.content()).contains("急性心肌梗死");
    }

    @Test
    @DisplayName("空结果场景：MOCK_EMPTY 关键词触发空结果")
    void emptyScenario() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "MOCK_EMPTY"));

        assertThat(response.content()).contains("\"departmentCode\":\"\"");
        assertThat(response.content()).contains("\"symptomKeywords\":[]");
    }

    @Test
    @DisplayName("超时场景：MOCK_TIMEOUT 关键词抛出可重试异常")
    void timeoutScenario() {
        assertThatThrownBy(() -> provider.generate(
                new AIProviderRequest("triage", "MOCK_TIMEOUT")))
                .isInstanceOf(AIProviderException.class)
                .satisfies(ex -> {
                    AIProviderException ape = (AIProviderException) ex;
                    assertThat(ape.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("非法JSON场景：MOCK_INVALID_JSON 关键词返回非JSON文本")
    void invalidJsonScenario() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "MOCK_INVALID_JSON"));

        assertThat(response.mock()).isTrue();
        assertThat(response.content()).doesNotStartWith("{");
        assertThat(response.content()).contains("这不是一个合法的 JSON");
    }

    @Test
    @DisplayName("不存在科室场景：MOCK_NOT_EXIST_DEPT 关键词返回不存在科室编码")
    void notExistDeptScenario() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "MOCK_NOT_EXIST_DEPT"));

        assertThat(response.content()).contains("DEPT_NOT_EXIST_999");
    }

    @Test
    @DisplayName("Provider异常场景：MOCK_PROVIDER_ERROR 关键词抛出不可重试异常")
    void providerErrorScenario() {
        assertThatThrownBy(() -> provider.generate(
                new AIProviderRequest("triage", "MOCK_PROVIDER_ERROR")))
                .isInstanceOf(AIProviderException.class)
                .satisfies(ex -> {
                    AIProviderException ape = (AIProviderException) ex;
                    assertThat(ape.isRetryable()).isFalse();
                    assertThat(ape.getHttpStatus()).isEqualTo(500);
                });
    }

    // ============================================================
    // 五个能力 Mock 响应格式测试
    // ============================================================

    @Test
    @DisplayName("分诊响应包含所有必填字段")
    void triageResponse_containsAllFields() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "主诉: 发热咳嗽"));

        String content = response.content();
        assertThat(content).contains("departmentCode");
        assertThat(content).contains("priority");
        assertThat(content).contains("symptomKeywords");
        assertThat(content).contains("reason");
        assertThat(content).contains("safetyNotice");
        assertThat(content).contains("emergencySuggested");
    }

    @Test
    @DisplayName("诊断响应包含所有必填字段")
    void diagnosisResponse_containsAllFields() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("diagnosis", "主诉: 胸痛"));

        String content = response.content();
        assertThat(content).contains("possibleDiagnoses");
        assertThat(content).contains("evidence");
        assertThat(content).contains("missingInformation");
        assertThat(content).contains("riskFactors");
        assertThat(content).contains("suggestedExaminations");
        assertThat(content).contains("disclaimer");
    }

    @Test
    @DisplayName("病历生成响应包含所有必填字段")
    void medicalRecordResponse_containsAllFields() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("medical_record", "主诉: 头痛"));

        String content = response.content();
        assertThat(content).contains("chiefComplaint");
        assertThat(content).contains("presentIllness");
        assertThat(content).contains("pastHistory");
        assertThat(content).contains("physicalExamination");
        assertThat(content).contains("preliminaryDiagnosis");
        assertThat(content).contains("treatmentSuggestion");
        assertThat(content).contains("disclaimer");
    }

    @Test
    @DisplayName("处方审核响应包含所有必填字段")
    void prescriptionReviewResponse_containsAllFields() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("prescription_review", "处方审核: 阿莫西林"));

        String content = response.content();
        assertThat(content).contains("riskLevel");
        assertThat(content).contains("allergyWarnings");
        assertThat(content).contains("interactionWarnings");
        assertThat(content).contains("dosageWarnings");
        assertThat(content).contains("contraindicationWarnings");
        assertThat(content).contains("suggestions");
        assertThat(content).contains("disclaimer");
    }

    @Test
    @DisplayName("结果解读响应包含所有必填字段")
    void resultInterpretationResponse_containsAllFields() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("result_interpretation", "血糖偏高"));

        String content = response.content();
        assertThat(content).contains("abnormalItems");
        assertThat(content).contains("plainLanguageExplanation");
        assertThat(content).contains("followUpAdvice");
        assertThat(content).contains("disclaimer");
    }

    @Test
    @DisplayName("所有响应包含安全声明")
    void allResponses_containSafetyNotice() {
        String[] capabilities = {"triage", "diagnosis", "medical_record", "prescription_review", "result_interpretation"};
        for (String cap : capabilities) {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest(cap, "测试输入"));
            // 各能力 disclaimer 措辞略有差异（分诊面向患者用"仅供参考"，其他面向医生用"仅供医生参考"），
            // 共同关键词为"AI 辅助生成"
            assertThat(response.content())
                    .as("能力 %s 的响应应包含安全声明", cap)
                    .contains("AI 辅助生成");
        }
    }

    @Test
    @DisplayName("所有响应标记为 mock")
    void allResponses_markedAsMock() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "测试"));
        assertThat(response.mock()).isTrue();
    }

    @Test
    @DisplayName("provider name 为 MOCK")
    void name_isMock() {
        assertThat(provider.name()).isEqualTo("MOCK");
    }
}
