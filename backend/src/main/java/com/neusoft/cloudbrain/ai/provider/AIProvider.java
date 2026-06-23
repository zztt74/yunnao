package com.neusoft.cloudbrain.ai.provider;

public interface AIProvider {

    String name();

    AIProviderResponse generate(AIProviderRequest request);
}
