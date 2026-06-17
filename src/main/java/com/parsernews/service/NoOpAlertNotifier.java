package com.parsernews.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(AlertNotifier.class)
public class NoOpAlertNotifier implements AlertNotifier {
    @Override
    public AlertNotificationResult send(String message) {
        return AlertNotificationResult.notSent(
                "DISABLED",
                "Telegram alerts are disabled; no external message was sent."
        );
    }
}
