package com.neusoft.cloudbrain.ai.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockAIProvider implements AIProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public AIProviderResponse generate(AIProviderRequest request) {
        return new AIProviderResponse(
                "Mock AI 基础能力已启用，结果仅供辅助参考，请由授权人员确认。",
                true);
    }
}
