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

    /**
     * Length Telegram applies its caption/text limits to: the caption is measured "after entities
     * parsing", so HTML markup does not count. Measuring the raw string instead over-counts by the
     * size of all the tags and needlessly drops the photo.
     */
    static int visibleLength(String html) {
        if (html == null || html.isEmpty()) {
            return 0;
        }
        String text = html.replaceAll("<[^>]+>", "");
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
        return text.length();
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
