package com.parsernews.service;

import com.parsernews.config.OpenAiAnalysisSettings;
import org.springframework.stereotype.Service;

@Service
public class OpenAiRuntimeSettingsService {
    private final OpenAiAnalysisSettings configSettings;
    private boolean runtimeOverridePresent;
    private boolean runtimeEnabled;
    private String runtimeApiKey;
    private String runtimeModel;
    private Integer runtimeMaxInputChars;

    public OpenAiRuntimeSettingsService(OpenAiAnalysisSettings configSettings) {
        this.configSettings = configSettings;
    }

    public synchronized EffectiveOpenAiSettings effectiveSettings() {
        boolean enabled = runtimeOverridePresent ? runtimeEnabled : configSettings.enabled();
        String apiKey = !isBlank(runtimeApiKey) ? runtimeApiKey : configSettings.apiKey();
        String model = !isBlank(runtimeModel) ? runtimeModel : configSettings.model();
        int maxInputChars = runtimeMaxInputChars != null && runtimeMaxInputChars > 0
                ? runtimeMaxInputChars
                : configSettings.maxInputChars();
        KeySource keySource = !isBlank(runtimeApiKey)
                ? KeySource.RUNTIME
                : !isBlank(configSettings.apiKey()) ? KeySource.ENV : KeySource.NONE;
        boolean configured = keySource != KeySource.NONE;
        String message = message(enabled, configured, keySource);
        return new EffectiveOpenAiSettings(
                enabled,
                configured,
                apiKey,
                model,
                maxInputChars,
                keySource,
                mask(apiKey),
                message
        );
    }

    public synchronized EffectiveOpenAiSettings update(boolean enabled, String apiKey, String model, Integer maxInputChars) {
        runtimeOverridePresent = true;
        runtimeEnabled = enabled;
        if (!enabled) {
            runtimeApiKey = null;
        } else if (!isBlank(apiKey)) {
            runtimeApiKey = apiKey.trim();
        }
        if (!isBlank(model)) {
            runtimeModel = model.trim();
        }
        if (maxInputChars != null && maxInputChars > 0) {
            runtimeMaxInputChars = maxInputChars;
        }
        return effectiveSettings();
    }

    public synchronized EffectiveOpenAiSettings clearRuntimeKeyAndDisable() {
        runtimeOverridePresent = true;
        runtimeEnabled = false;
        runtimeApiKey = null;
        return effectiveSettings();
    }

    private String message(boolean enabled, boolean configured, KeySource keySource) {
        if (!enabled) {
            return "OpenAI AI Review is disabled.";
        }
        if (!configured) {
            return "OpenAI AI Review is enabled but no API key is configured.";
        }
        return "OpenAI AI Review is enabled using " + keySource.name().toLowerCase() + " key.";
    }

    private String mask(String apiKey) {
        if (isBlank(apiKey)) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, Math.min(3, trimmed.length())) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum KeySource {
        NONE,
        ENV,
        RUNTIME
    }

    public record EffectiveOpenAiSettings(
            boolean enabled,
            boolean configured,
            String apiKey,
            String model,
            int maxInputChars,
            KeySource keySource,
            String keyMasked,
            String message
    ) {
    }
}
