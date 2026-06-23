package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai.analysis")
public record OpenAiAnalysisSettings(
        boolean enabled,
        String apiKey,
        String model,
        int maxInputChars
) {
    public OpenAiAnalysisSettings {
        model = model == null || model.isBlank() ? "gpt-4.1-mini" : model;
        maxInputChars = maxInputChars <= 0 ? 12000 : maxInputChars;
    }
}
