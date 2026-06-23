package com.neusoft.cloudbrain.ai.provider;

public record AIProviderRequest(
        String capability,
        String sanitizedInput) {
}
