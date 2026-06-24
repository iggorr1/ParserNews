package com.parsernews.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class TelegramAlertNotifier implements AlertNotifier {
    private final TelegramRuntimeSettingsService settingsService;
    private final RestClient restClient;

    public TelegramAlertNotifier(TelegramRuntimeSettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
    }

    @Override
    public AlertNotificationResult send(String message) {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled()) {
            return AlertNotificationResult.notSent(
                    "DISABLED",
                    "Telegram is disabled; no external message was sent."
            );
        }
        if (isBlank(settings.botToken()) || isBlank(settings.chatId())) {
            return AlertNotificationResult.notSent(
                    "CONFIG_MISSING",
                    "Telegram is enabled but bot token or chat id is missing."
            );
        }
        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", settings.botToken())
                    .body(Map.of(
                            "chat_id", settings.chatId(),
                            "text", message,
                            "disable_web_page_preview", true
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return AlertNotificationResult.sent("SENT", "Telegram alert message was sent.");
        } catch (RestClientException exception) {
            return AlertNotificationResult.notSent("SEND_FAILED", "Telegram alert send failed.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
