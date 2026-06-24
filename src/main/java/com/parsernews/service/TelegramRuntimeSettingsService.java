package com.parsernews.service;

import com.parsernews.config.TelegramAlertSettings;
import org.springframework.stereotype.Service;

@Service
public class TelegramRuntimeSettingsService {
    private final TelegramAlertSettings configSettings;
    private boolean runtimeOverridePresent;
    private boolean runtimeEnabled;
    private String runtimeBotToken;
    private String runtimeChatId;

    public TelegramRuntimeSettingsService(TelegramAlertSettings configSettings) {
        this.configSettings = configSettings;
    }

    public synchronized EffectiveTelegramSettings effectiveSettings() {
        boolean enabled = runtimeOverridePresent ? runtimeEnabled : configSettings.enabled();
        String botToken = !isBlank(runtimeBotToken) ? runtimeBotToken : configSettings.botToken();
        String chatId = !isBlank(runtimeChatId) ? runtimeChatId : configSettings.chatId();
        SecretSource tokenSource = !isBlank(runtimeBotToken)
                ? SecretSource.RUNTIME
                : !isBlank(configSettings.botToken()) ? SecretSource.ENV : SecretSource.NONE;
        SecretSource chatIdSource = !isBlank(runtimeChatId)
                ? SecretSource.RUNTIME
                : !isBlank(configSettings.chatId()) ? SecretSource.ENV : SecretSource.NONE;
        boolean configured = tokenSource != SecretSource.NONE && chatIdSource != SecretSource.NONE;
        return new EffectiveTelegramSettings(
                enabled,
                configured,
                botToken,
                chatId,
                tokenSource,
                chatIdSource,
                mask(botToken),
                mask(chatId),
                message(enabled, configured, tokenSource, chatIdSource)
        );
    }

    public synchronized EffectiveTelegramSettings update(boolean enabled, String botToken, String chatId) {
        runtimeOverridePresent = true;
        runtimeEnabled = enabled;
        if (!enabled) {
            runtimeBotToken = null;
            runtimeChatId = null;
        } else {
            if (!isBlank(botToken)) {
                runtimeBotToken = botToken.trim();
            }
            if (!isBlank(chatId)) {
                runtimeChatId = chatId.trim();
            }
        }
        return effectiveSettings();
    }

    public synchronized EffectiveTelegramSettings clearRuntimeSettingsAndDisable() {
        runtimeOverridePresent = true;
        runtimeEnabled = false;
        runtimeBotToken = null;
        runtimeChatId = null;
        return effectiveSettings();
    }

    private String message(boolean enabled, boolean configured, SecretSource tokenSource, SecretSource chatIdSource) {
        if (!enabled) {
            return "Telegram is disabled; no external messages will be sent.";
        }
        if (!configured) {
            return "Telegram is enabled but bot token or chat id is missing.";
        }
        return "Telegram is enabled using " + tokenSource.name().toLowerCase()
                + " token and " + chatIdSource.name().toLowerCase() + " chat id.";
    }

    private String mask(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, Math.min(3, trimmed.length())) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum SecretSource {
        NONE,
        ENV,
        RUNTIME
    }

    public record EffectiveTelegramSettings(
            boolean enabled,
            boolean configured,
            String botToken,
            String chatId,
            SecretSource tokenSource,
            SecretSource chatIdSource,
            String tokenMasked,
            String chatIdMasked,
            String message
    ) {
        public boolean sendAllowed() {
            return enabled && configured;
        }
    }
}
