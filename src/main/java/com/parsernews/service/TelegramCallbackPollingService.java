package com.parsernews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.persistence.ManualReviewStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TelegramCallbackPollingService {
    private static final String TELEGRAM_BASE = "https://api.telegram.org";
    private static final int POLL_TIMEOUT_SECONDS = 10;
    // review buttons: "qr|STATUS|groupKey" ; refresh-price button: "rp|groupKey"
    private static final String CB_PREFIX = "qr|";
    private static final String RP_PREFIX = "rp|";

    private final AtomicLong offset = new AtomicLong(0);
    private final AtomicBoolean webhookDeleted = new AtomicBoolean(false);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TelegramRuntimeSettingsService settingsService;
    private final DealGroupReviewService dealGroupReviewService;
    private final DealGroupingService dealGroupingService;
    private final AutoDealGroupDispatchService dispatchService;

    public TelegramCallbackPollingService(
            ObjectMapper objectMapper,
            TelegramRuntimeSettingsService settingsService,
            DealGroupReviewService dealGroupReviewService,
            DealGroupingService dealGroupingService,
            AutoDealGroupDispatchService dispatchService
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.dealGroupReviewService = dealGroupReviewService;
        this.dealGroupingService = dealGroupingService;
        this.dispatchService = dispatchService;
    }

    @PostConstruct
    public void deleteWebhookOnStartup() {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled() || isBlank(settings.botToken())) return;
        tryDeleteWebhook(settings.botToken());
    }

    @Scheduled(fixedDelay = 200)
    public void poll() {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = settingsService.effectiveSettings();
        if (!settings.enabled() || isBlank(settings.botToken())) return;

        if (webhookDeleted.compareAndSet(false, true)) {
            tryDeleteWebhook(settings.botToken());
        }

        try {
            String url = TELEGRAM_BASE + "/bot" + settings.botToken()
                    + "/getUpdates?timeout=" + POLL_TIMEOUT_SECONDS
                    + "&offset=" + offset.get()
                    + "&allowed_updates=%5B%22callback_query%22%5D";

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(POLL_TIMEOUT_SECONDS + 5))
                    .header("Accept", "application/json")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            processUpdates(response.body(), settings.botToken());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    private void processUpdates(String body, String token) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!root.path("ok").asBoolean()) return;

        JsonNode results = root.path("result");
        if (!results.isArray()) return;

        for (JsonNode update : results) {
            long updateId = update.path("update_id").asLong();
            offset.set(updateId + 1);

            JsonNode callback = update.path("callback_query");
            if (callback.isMissingNode()) continue;

            String callbackId = callback.path("id").asText();
            String data = callback.path("data").asText("");
            processCallback(data, callbackId, callback.path("message"), token);
        }
    }

    private void processCallback(String data, String callbackId, JsonNode message, String token) {
        if (data.startsWith(RP_PREFIX)) {
            processRefresh(data.substring(RP_PREFIX.length()), callbackId, message, token);
            return;
        }
        if (!data.startsWith(CB_PREFIX)) return;

        String[] parts = data.substring(CB_PREFIX.length()).split("\\|", 2);
        if (parts.length != 2) return;

        String statusStr = parts[0];
        String groupKey = parts[1];

        ManualReviewStatus status;
        try {
            status = ManualReviewStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        boolean groupExists = dealGroupingService.group(groupKey).isPresent();
        if (!groupExists) {
            answerCallback(callbackId, token, "Group not found");
            return;
        }

        dealGroupReviewService.update(groupKey, status, null, null);
        String text = status == ManualReviewStatus.USEFUL ? "✓ Marked as Useful" : "✗ Marked as Ignored";
        answerCallback(callbackId, token, text);
    }

    private void processRefresh(String groupKey, String callbackId, JsonNode message, String token) {
        String chatId = message.path("chat").path("id").asText("");
        long messageId = message.path("message_id").asLong(0);
        boolean isPhoto = message.has("photo");
        if (chatId.isEmpty() || messageId == 0) {
            answerCallback(callbackId, token, "Can't refresh this message");
            return;
        }
        String result;
        try {
            result = dispatchService.refreshPriceMessage(groupKey, chatId, messageId, isPhoto);
        } catch (RuntimeException exception) {
            result = "Refresh failed";
        }
        answerCallback(callbackId, token, result);
    }

    private void tryDeleteWebhook(String token) {
        try {
            String url = TELEGRAM_BASE + "/bot" + token + "/deleteWebhook?drop_pending_updates=false";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private void answerCallback(String callbackId, String token, String text) {
        try {
            String url = TELEGRAM_BASE + "/bot" + token + "/answerCallbackQuery";
            String bodyJson = objectMapper.writeValueAsString(Map.of(
                    "callback_query_id", callbackId,
                    "text", text,
                    "show_alert", false
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
