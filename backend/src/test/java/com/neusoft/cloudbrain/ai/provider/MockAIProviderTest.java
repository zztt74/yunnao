package com.neusoft.cloudbrain.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockAIProviderTest {

    @Test
    void mockResponseIsExplicitlyMarkedAndNeverClaimsFormalDiagnosis() {
        AIProvider provider = new MockAIProvider();

        AIProviderResponse response = provider.generate(
                new AIProviderRequest("SCAFFOLD_CHECK", "verify provider boundary"));

        assertThat(provider.name()).isEqualTo("mock");
        assertThat(response.mock()).isTrue();
        assertThat(response.content()).contains("仅供辅助参考");
        assertThat(response.content()).doesNotContain("正式诊断");
    }
}
