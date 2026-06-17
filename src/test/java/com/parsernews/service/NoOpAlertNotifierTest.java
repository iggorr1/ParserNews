package com.parsernews.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpAlertNotifierTest {
    @Test
    void noOpNotifierReturnsClearSafeResponse() {
        NoOpAlertNotifier notifier = new NoOpAlertNotifier();

        AlertNotifier.AlertNotificationResult result = notifier.send("test message");

        assertThat(result.sent()).isFalse();
        assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(result.reason()).contains("disabled");
    }
}
