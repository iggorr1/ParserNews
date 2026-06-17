package com.parsernews.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "alerts.telegram", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAlertNotifier implements AlertNotifier {
    @Override
    public AlertNotificationResult send(String message) {
        return AlertNotificationResult.notSent(
                "DISABLED",
                "Telegram alerts are disabled; no external message was sent."
        );
    }
}
