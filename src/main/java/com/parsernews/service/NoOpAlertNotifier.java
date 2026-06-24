package com.parsernews.service;

public class NoOpAlertNotifier implements AlertNotifier {
    @Override
    public AlertNotificationResult send(String message) {
        return AlertNotificationResult.notSent(
                "DISABLED",
                "Telegram alerts are disabled; no external message was sent."
        );
    }
}
