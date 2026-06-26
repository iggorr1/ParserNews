package com.parsernews.service;

import java.util.List;

public interface AlertNotifier {
    AlertNotificationResult send(String message);

    default AlertNotificationResult sendWithButtons(String message, List<InlineButton> buttons) {
        return send(message);
    }

    record InlineButton(String text, String url) {
    }

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
