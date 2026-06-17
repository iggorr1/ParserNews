package com.parsernews.service;

public interface AlertNotifier {
    AlertNotificationResult send(String message);

    record AlertNotificationResult(
            boolean sent,
            String status,
            String reason
    ) {
        public static AlertNotificationResult sent(String status, String reason) {
            return new AlertNotificationResult(true, status, reason);
        }

        public static AlertNotificationResult notSent(String status, String reason) {
            return new AlertNotificationResult(false, status, reason);
        }
    }
}
