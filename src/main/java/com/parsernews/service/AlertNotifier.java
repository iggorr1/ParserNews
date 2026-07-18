package com.parsernews.service;

import java.util.List;

public interface AlertNotifier {
    AlertNotificationResult send(String message);

    default AlertNotificationResult sendWithButtons(String message, List<InlineButton> buttons) {
        return send(message);
    }

    /**
     * Sends a photo with a caption and inline buttons. Implementations that cannot send a photo
     * fall back to the text-only path so the alert still goes out.
     */
    default AlertNotificationResult sendPhotoWithButtons(byte[] png, String caption, List<InlineButton> buttons) {
        return sendWithButtons(caption, buttons);
    }

    /** Edits an existing photo message in place (new image + caption + buttons). */
    default AlertNotificationResult editPhotoWithButtons(
            String chatId, long messageId, byte[] png, String caption, List<InlineButton> buttons) {
        return AlertNotificationResult.notSent("UNSUPPORTED", "This notifier cannot edit messages.");
    }

    /** Edits an existing text message in place (new text + buttons). */
    default AlertNotificationResult editTextWithButtons(
            String chatId, long messageId, String text, List<InlineButton> buttons) {
        return AlertNotificationResult.notSent("UNSUPPORTED", "This notifier cannot edit messages.");
    }

    record InlineButton(String text, String url, String callbackData) {
        public static InlineButton url(String text, String url) {
            return new InlineButton(text, url, null);
        }

        public static InlineButton callback(String text, String callbackData) {
            return new InlineButton(text, null, callbackData);
        }
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
