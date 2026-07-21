package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelegramAlertNotifier implements AlertNotifier {
    // Telegram hard-caps a photo caption at 1024 chars; a text message allows 4096.
    private static final int CAPTION_LIMIT = 1024;

    private final TelegramRuntimeSettingsService settingsService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TelegramAlertNotifier(
            TelegramRuntimeSettingsService settingsService,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org").build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AlertNotificationResult send(String message) {
        return sendWithButtons(message, List.of());
    }

    @Override
    public AlertNotificationResult sendWithButtons(String message, List<InlineButton> buttons) {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled()) {
            return AlertNotificationResult.notSent("DISABLED", "Telegram is disabled; no external message was sent.");
        }
        if (isBlank(settings.botToken()) || isBlank(settings.chatId())) {
            return AlertNotificationResult.notSent("CONFIG_MISSING", "Telegram is enabled but bot token or chat id is missing.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", settings.chatId());
            body.put("text", message);
            body.put("parse_mode", "HTML");
            body.put("disable_web_page_preview", true);
            if (!buttons.isEmpty()) {
                List<List<Map<String, String>>> keyboard = new ArrayList<>();
                List<Map<String, String>> row = new ArrayList<>();
                for (InlineButton button : buttons) {
                    Map<String, String> btn = new LinkedHashMap<>();
                    btn.put("text", button.text());
                    if (button.callbackData() != null) {
                        btn.put("callback_data", button.callbackData());
                    } else if (button.url() != null) {
                        btn.put("url", button.url());
                    }
                    row.add(btn);
                }
                keyboard.add(row);
                body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            }
            restClient.post()
                    .uri("/bot{token}/sendMessage", settings.botToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return AlertNotificationResult.sent("SENT", "Telegram alert message was sent.");
        } catch (RestClientException exception) {
            return AlertNotificationResult.notSent("SEND_FAILED", "Telegram alert send failed: " + exception.getMessage());
        }
    }

    @Override
    public AlertNotificationResult sendPhotoWithButtons(byte[] png, String caption, List<InlineButton> buttons) {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled()) {
            return AlertNotificationResult.notSent("DISABLED", "Telegram is disabled; no external message was sent.");
        }
        if (isBlank(settings.botToken()) || isBlank(settings.chatId())) {
            return AlertNotificationResult.notSent("CONFIG_MISSING", "Telegram is enabled but bot token or chat id is missing.");
        }
        // A caption that would overflow Telegram's 1024-char limit means detail would be lost, so
        // send it as a full text message instead of silently truncating the analysis.
        if (png == null || AlertNotifier.visibleLength(caption) > CAPTION_LIMIT) {
            return sendWithButtons(caption, buttons);
        }
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", settings.chatId());
            form.add("caption", caption);
            form.add("parse_mode", "HTML");
            form.add("photo", new ByteArrayResource(png) {
                @Override
                public String getFilename() {
                    return "chart.png";
                }
            });
            if (!buttons.isEmpty()) {
                form.add("reply_markup", objectMapper.writeValueAsString(
                        Map.of("inline_keyboard", List.of(buttonRow(buttons)))));
            }
            restClient.post()
                    .uri("/bot{token}/sendPhoto", settings.botToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return AlertNotificationResult.sent("SENT", "Telegram photo alert was sent.");
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Network / API failure: still get the alert out as text rather than dropping it.
            return sendWithButtons(caption, buttons);
        }
    }

    @Override
    public AlertNotificationResult editPhotoWithButtons(
            String chatId, long messageId, byte[] png, String caption, List<InlineButton> buttons) {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled() || isBlank(settings.botToken())) {
            return AlertNotificationResult.notSent("DISABLED", "Telegram is disabled or misconfigured.");
        }
        // A caption over the photo limit can't be an edited photo caption; edit as text instead.
        if (png == null || AlertNotifier.visibleLength(caption) > CAPTION_LIMIT) {
            return editTextWithButtons(chatId, messageId, caption, buttons);
        }
        try {
            Map<String, Object> media = new LinkedHashMap<>();
            media.put("type", "photo");
            media.put("media", "attach://chart");
            media.put("caption", caption);
            media.put("parse_mode", "HTML");

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);
            form.add("message_id", messageId);
            form.add("media", objectMapper.writeValueAsString(media));
            form.add("chart", new ByteArrayResource(png) {
                @Override
                public String getFilename() {
                    return "chart.png";
                }
            });
            if (!buttons.isEmpty()) {
                form.add("reply_markup", objectMapper.writeValueAsString(
                        Map.of("inline_keyboard", List.of(buttonRow(buttons)))));
            }
            restClient.post()
                    .uri("/bot{token}/editMessageMedia", settings.botToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return AlertNotificationResult.sent("EDITED", "Telegram photo message was updated.");
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            return AlertNotificationResult.notSent("EDIT_FAILED", "Telegram edit failed: " + exception.getMessage());
        }
    }

    @Override
    public AlertNotificationResult editTextWithButtons(
            String chatId, long messageId, String text, List<InlineButton> buttons) {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled() || isBlank(settings.botToken())) {
            return AlertNotificationResult.notSent("DISABLED", "Telegram is disabled or misconfigured.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("disable_web_page_preview", true);
            if (!buttons.isEmpty()) {
                body.put("reply_markup", Map.of("inline_keyboard", List.of(buttonRow(buttons))));
            }
            restClient.post()
                    .uri("/bot{token}/editMessageText", settings.botToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return AlertNotificationResult.sent("EDITED", "Telegram text message was updated.");
        } catch (RestClientException exception) {
            return AlertNotificationResult.notSent("EDIT_FAILED", "Telegram edit failed: " + exception.getMessage());
        }
    }

    private List<Map<String, String>> buttonRow(List<InlineButton> buttons) {
        List<Map<String, String>> row = new ArrayList<>();
        for (InlineButton button : buttons) {
            Map<String, String> btn = new LinkedHashMap<>();
            btn.put("text", button.text());
            if (button.callbackData() != null) {
                btn.put("callback_data", button.callbackData());
            } else if (button.url() != null) {
                btn.put("url", button.url());
            }
            row.add(btn);
        }
        return row;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
