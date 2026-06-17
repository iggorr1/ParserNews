package com.parsernews.service;

import com.parsernews.config.TelegramAlertSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class AlertNotifierContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NoOpAlertNotifier.class, TelegramAlertNotifier.class);

    @Test
    void defaultConfigCreatesNoOpNotifier() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AlertNotifier.class);
            assertThat(context.getBean(AlertNotifier.class)).isInstanceOf(NoOpAlertNotifier.class);
        });
    }

    @Test
    void telegramDisabledCreatesNoOpNotifier() {
        contextRunner
                .withPropertyValues("alerts.telegram.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AlertNotifier.class);
                    assertThat(context.getBean(AlertNotifier.class)).isInstanceOf(NoOpAlertNotifier.class);
                });
    }

    @Test
    void telegramEnabledCreatesTelegramNotifierOnly() {
        contextRunner
                .withBean(TelegramAlertSettings.class, () -> new TelegramAlertSettings(true, "token", "chat"))
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withPropertyValues("alerts.telegram.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AlertNotifier.class);
                    assertThat(context.getBean(AlertNotifier.class)).isInstanceOf(TelegramAlertNotifier.class);
                });
    }
}
