package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alerts.telegram")
public record TelegramAlertSettings(
        boolean enabled,
        String botToken,
        String chatId
) {
}
