package com.neusoft.cloudbrain.ai.integration;

import com.neusoft.cloudbrain.CloudBrainApplication;
import com.neusoft.cloudbrain.ai.config.AIProperties;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.ai.provider.AIProviderResponse;
import com.neusoft.cloudbrain.ai.provider.MockAIProvider;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.auth.config.AdminInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MockAIProvider 端到端集成测试
 *
 * 验证（IT-2）：
 * - @ConditionalOnProperty 正确装配 MockAIProvider
 * - AIProperties 配置绑定生效
 * - 7 场景分流：正常 / 高风险 / 空 / 超时 / 非法 JSON / 不存在科室 / 异常
 * - 医疗关键词路由
 * - 所有响应包含 mock=true 和安全声明
 *
 * 运行方式：mvn test -Dtest="MockAIProviderIT"
 */
@ActiveProfiles("test")
@SpringBootTest(classes = CloudBrainApplication.class)
@MockBean(AdminInitializer.class)
@DisplayName("IT2 - MockAIProvider 端到端")
class MockAIProviderIT {

    @Autowired
    private MockAIProvider provider;

    @Autowired
    private AIProperties properties;

    @Test
    @DisplayName("Spring 上下文正确装配 MockAIProvider Bean")
    void context_loadsMockProvider() {
        assertThat(provider).isNotNull();
        assertThat(provider.name()).isEqualTo("MOCK");
    }

    @Test
    @DisplayName("AIProperties 配置绑定成功（默认值）")
    void properties_boundCorrectly() {
        assertThat(properties.getMode()).isEqualTo(AIProperties.Mode.MOCK);
        assertThat(properties.getMaxRetries()).isEqualTo(1);
        assertThat(properties.getTimeoutMs()).isEqualTo(8000);
        assertThat(properties.getMock().getTimeoutKeyword()).isEqualTo("MOCK_TIMEOUT");
        assertThat(properties.getMock().getInvalidJsonKeyword()).isEqualTo("MOCK_INVALID_JSON");
    }

    @Test
    @DisplayName("所有响应包含 mock=true 标识")
    void allResponses_areMock() {
        AIProviderResponse response = provider.generate(
                new AIProviderRequest("triage", "发热"));

        assertThat(response.mock()).isTrue();
    }

    // ============================================================
    // 7 场景分流
    // ============================================================

    @Nested
    @DisplayName("7 Mock 场景")
    class SevenScenarios {

        @Test
        @DisplayName("正常场景：发热关键词 → 内科 MEDIUM")
        void normal_fever() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "发热咳嗽2天"));

            assertThat(response.content()).contains("DEPT_INTERNAL");
            assertThat(response.content()).contains("MEDIUM");
            assertThat(response.content()).contains("仅供参考");
        }

        @Test
        @DisplayName("高风险场景：MOCK_HIGH_RISK → 高风险响应")
        void highRisk() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "MOCK_HIGH_RISK 胸痛"));

            assertThat(response.content()).contains("DEPT_CARDIOLOGY");
            assertThat(response.content()).contains("HIGH");
            assertThat(response.model()).isEqualTo("mock-high-risk");
        }

        @Test
        @DisplayName("空结果场景：MOCK_EMPTY → LOW 优先级+默认科室")
        void emptyResult() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "MOCK_EMPTY"));

            assertThat(response.content()).contains("\"LOW\"");
            assertThat(response.content()).contains("DEPT_INTERNAL");
            assertThat(response.model()).isEqualTo("mock-empty");
        }

        @Test
        @DisplayName("超时场景：MOCK_TIMEOUT → AIProviderException")
        void timeout() {
            assertThatThrownBy(() -> provider.generate(
                    new AIProviderRequest("triage", "MOCK_TIMEOUT 头痛")))
                    .isInstanceOf(AIProviderException.class)
                    .satisfies(ex -> {
                        AIProviderException ae = (AIProviderException) ex;
                        assertThat(ae.isRetryable()).isTrue();
                    });
        }

        @Test
        @DisplayName("非法 JSON 场景：MOCK_INVALID_JSON → 非 JSON 文本")
        void invalidJson() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "MOCK_INVALID_JSON"));

            assertThat(response.content()).doesNotContain("{");
            assertThat(response.model()).isEqualTo("mock-invalid-json");
        }

        @Test
        @DisplayName("不存在科室场景：MOCK_NOT_EXIST_DEPT → DEPT_NOT_EXIST_999")
        void notExistDept() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "MOCK_NOT_EXIST_DEPT"));

            assertThat(response.content()).contains("DEPT_NOT_EXIST_999");
            assertThat(response.model()).isEqualTo("mock-not-exist-dept");
        }

        @Test
        @DisplayName("Provider 异常场景：MOCK_PROVIDER_ERROR → AIProviderException")
        void providerError() {
            assertThatThrownBy(() -> provider.generate(
                    new AIProviderRequest("triage", "MOCK_PROVIDER_ERROR 外伤")))
                    .isInstanceOf(AIProviderException.class)
                    .satisfies(ex -> {
                        AIProviderException ae = (AIProviderException) ex;
                        assertThat(ae.isRetryable()).isFalse();
                        assertThat(ae.getHttpStatus()).isEqualTo(500);
                    });
        }
    }

    // ============================================================
    // 医疗关键词路由
    // ============================================================

    @Nested
    @DisplayName("医疗关键词路由")
    class MedicalKeywordRouting {

        @Test
        @DisplayName("胸痛 → 心血管内科 HIGH")
        void chestPain() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "胸痛伴出汗2小时"));

            assertThat(response.content()).contains("DEPT_CARDIOLOGY");
            assertThat(response.content()).contains("HIGH");
            assertThat(response.content()).contains("emergencySuggested\":true");
        }

        @Test
        @DisplayName("头痛 → 神经内科 MEDIUM")
        void headache() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "头痛3天"));

            assertThat(response.content()).contains("DEPT_NEUROLOGY");
            assertThat(response.content()).contains("MEDIUM");
        }

        @Test
        @DisplayName("外伤 → 普通外科 MEDIUM")
        void trauma() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "外伤骨折1小时"));

            assertThat(response.content()).contains("DEPT_GENERAL_SURGERY");
        }

        @Test
        @DisplayName("儿童 → 儿科 MEDIUM")
        void child() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "仅患儿"));

            assertThat(response.content()).contains("DEPT_PEDIATRICS");
        }

        @Test
        @DisplayName("不明确症状 → 内科 LOW")
        void unclearSymptoms() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "感觉不舒服"));

            assertThat(response.content()).contains("DEPT_INTERNAL");
            assertThat(response.content()).contains("LOW");
        }

        @Test
        @DisplayName("呼吸困难 → 急诊 EMERGENCY")
        void dyspnea() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("triage", "呼吸困难1小时"));

            assertThat(response.content()).contains("DEPT_EMERGENCY");
            assertThat(response.content()).contains("EMERGENCY");
            assertThat(response.content()).contains("emergencySuggested\":true");
        }
    }

    // ============================================================
    // 能力路由
    // ============================================================

    @Nested
    @DisplayName("五种能力正常路由")
    class CapabilityRouting {

        @Test
        @DisplayName("诊断能力 → 包含 possibleDiagnoses")
        void diagnosis() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("diagnosis", "胸痛"));

            assertThat(response.content()).contains("possibleDiagnoses");
            assertThat(response.content()).contains("disclaimer");
        }

        @Test
        @DisplayName("病历能力 → 包含 chiefComplaint")
        void medicalRecord() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("medical_record", "咳嗽发热"));

            assertThat(response.content()).contains("chiefComplaint");
            assertThat(response.content()).contains("preliminaryDiagnosis");
        }

        @Test
        @DisplayName("处方审核能力 → 包含 riskLevel")
        void prescriptionReview() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("prescription_review", "处方审核"));

            assertThat(response.content()).contains("riskLevel");
            assertThat(response.content()).contains("recommendations");
        }

        @Test
        @DisplayName("结果解读能力 → 包含 abnormalItems")
        void resultInterpretation() {
            AIProviderResponse response = provider.generate(
                    new AIProviderRequest("result_interpretation", "血常规偏高"));

            assertThat(response.content()).contains("abnormalItems");
            assertThat(response.content()).contains("plainLanguageExplanation");
        }
    }
}
