package com.parsernews.service;

import com.parsernews.config.TelegramAlertSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class AlertNotifierContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TelegramAlertNotifier.class)
            .withBean(TelegramAlertSettings.class, () -> new TelegramAlertSettings(false, "", ""))
            .withBean(TelegramRuntimeSettingsService.class, () -> new TelegramRuntimeSettingsService(new TelegramAlertSettings(false, "", "")))
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void defaultConfigCreatesSafeTelegramNotifier() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AlertNotifier.class);
            assertThat(context.getBean(AlertNotifier.class)).isInstanceOf(TelegramAlertNotifier.class);
        });
    }

    @Test
    void telegramDisabledNotifierReturnsNoExternalSend() {
        contextRunner
                .withPropertyValues("alerts.telegram.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AlertNotifier.class);
                    AlertNotifier.AlertNotificationResult result = context.getBean(AlertNotifier.class).send("test");
                    assertThat(result.sent()).isFalse();
                    assertThat(result.status()).isEqualTo("DISABLED");
                });
    }

    @Test
    void telegramEnabledCreatesTelegramNotifierOnly() {
        contextRunner
                .withPropertyValues("alerts.telegram.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AlertNotifier.class);
                    assertThat(context.getBean(AlertNotifier.class)).isInstanceOf(TelegramAlertNotifier.class);
                });
    }
}
