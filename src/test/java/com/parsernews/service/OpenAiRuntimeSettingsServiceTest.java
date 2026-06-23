package com.parsernews.service;

import com.parsernews.config.OpenAiAnalysisSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiRuntimeSettingsServiceTest {
    @Test
    void envFallbackWorksWhenRuntimeSettingsAreAbsent() {
        OpenAiRuntimeSettingsService service = new OpenAiRuntimeSettingsService(
                new OpenAiAnalysisSettings(true, "sk-env-1234567890abcd", "gpt-4.1-mini", 12000)
        );

        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings = service.effectiveSettings();

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.configured()).isTrue();
        assertThat(settings.keySource()).isEqualTo(OpenAiRuntimeSettingsService.KeySource.ENV);
        assertThat(settings.apiKey()).isEqualTo("sk-env-1234567890abcd");
        assertThat(settings.keyMasked()).isEqualTo("sk-...abcd");
    }

    @Test
    void runtimeKeyOverridesEnvKeyAndIsMasked() {
        OpenAiRuntimeSettingsService service = new OpenAiRuntimeSettingsService(
                new OpenAiAnalysisSettings(true, "sk-env-1234567890abcd", "gpt-4.1-mini", 12000)
        );

        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings = service.update(
                true,
                "sk-runtime-0987654321wxyz",
                "gpt-4.1-mini",
                9000
        );

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.configured()).isTrue();
        assertThat(settings.apiKey()).isEqualTo("sk-runtime-0987654321wxyz");
        assertThat(settings.keySource()).isEqualTo(OpenAiRuntimeSettingsService.KeySource.RUNTIME);
        assertThat(settings.keyMasked()).isEqualTo("sk-...wxyz");
        assertThat(settings.keyMasked()).doesNotContain("0987654321");
        assertThat(settings.maxInputChars()).isEqualTo(9000);
    }

    @Test
    void disablingClearsRuntimeKeyAndStopsEffectiveOpenAi() {
        OpenAiRuntimeSettingsService service = new OpenAiRuntimeSettingsService(
                new OpenAiAnalysisSettings(true, "sk-env-1234567890abcd", "gpt-4.1-mini", 12000)
        );
        service.update(true, "sk-runtime-0987654321wxyz", null, null);

        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings = service.update(false, null, null, null);

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.configured()).isTrue();
        assertThat(settings.keySource()).isEqualTo(OpenAiRuntimeSettingsService.KeySource.ENV);
        assertThat(settings.apiKey()).isEqualTo("sk-env-1234567890abcd");
    }

    @Test
    void deleteClearsRuntimeKeyAndDisablesRuntimeOpenAi() {
        OpenAiRuntimeSettingsService service = new OpenAiRuntimeSettingsService(
                new OpenAiAnalysisSettings(false, "", "gpt-4.1-mini", 12000)
        );
        service.update(true, "sk-runtime-0987654321wxyz", null, null);

        OpenAiRuntimeSettingsService.EffectiveOpenAiSettings settings = service.clearRuntimeKeyAndDisable();

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.configured()).isFalse();
        assertThat(settings.keySource()).isEqualTo(OpenAiRuntimeSettingsService.KeySource.NONE);
        assertThat(settings.apiKey()).isBlank();
    }
}
