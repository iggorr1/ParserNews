package com.parsernews.service;

import com.parsernews.config.TelegramAlertSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramRuntimeSettingsServiceTest {
    @Test
    void envFallbackWorksWhenRuntimeSettingsAreAbsent() {
        TelegramRuntimeSettingsService service = new TelegramRuntimeSettingsService(
                new TelegramAlertSettings(true, "123456:env-token-secret", "987654321")
        );

        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = service.effectiveSettings();

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.configured()).isTrue();
        assertThat(settings.tokenSource()).isEqualTo(TelegramRuntimeSettingsService.SecretSource.ENV);
        assertThat(settings.chatIdSource()).isEqualTo(TelegramRuntimeSettingsService.SecretSource.ENV);
        assertThat(settings.botToken()).isEqualTo("123456:env-token-secret");
        assertThat(settings.tokenMasked()).doesNotContain("env-token");
        assertThat(settings.chatIdMasked()).isEqualTo("987...4321");
    }

    @Test
    void runtimeSettingsOverrideEnvAndAreMasked() {
        TelegramRuntimeSettingsService service = new TelegramRuntimeSettingsService(
                new TelegramAlertSettings(true, "123456:env-token-secret", "987654321")
        );

        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = service.update(
                true,
                "999999:runtime-token-secret",
                "123456789"
        );

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.configured()).isTrue();
        assertThat(settings.botToken()).isEqualTo("999999:runtime-token-secret");
        assertThat(settings.chatId()).isEqualTo("123456789");
        assertThat(settings.tokenSource()).isEqualTo(TelegramRuntimeSettingsService.SecretSource.RUNTIME);
        assertThat(settings.chatIdSource()).isEqualTo(TelegramRuntimeSettingsService.SecretSource.RUNTIME);
        assertThat(settings.tokenMasked()).doesNotContain("runtime-token");
        assertThat(settings.chatIdMasked()).isEqualTo("123...6789");
    }

    @Test
    void clearRuntimeSettingsDisablesRuntimeTelegramAndClearsSecrets() {
        TelegramRuntimeSettingsService service = new TelegramRuntimeSettingsService(
                new TelegramAlertSettings(false, "", "")
        );
        service.update(true, "999999:runtime-token-secret", "123456789");

        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = service.clearRuntimeSettingsAndDisable();

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.configured()).isFalse();
        assertThat(settings.botToken()).isBlank();
        assertThat(settings.chatId()).isBlank();
        assertThat(settings.tokenSource()).isEqualTo(TelegramRuntimeSettingsService.SecretSource.NONE);
        assertThat(settings.chatIdSource()).isEqualTo(TelegramRuntimeSettingsService.SecretSource.NONE);
    }
}
