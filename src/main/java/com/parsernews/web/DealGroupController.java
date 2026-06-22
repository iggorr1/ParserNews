package com.parsernews.web;

import com.parsernews.config.TelegramAlertSettings;
import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.AlertNotifier;
import com.parsernews.service.DealGroupReviewService;
import com.parsernews.service.DealGroupingService;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
public class DealGroupController {
    private final DealGroupingService dealGroupingService;
    private final DealGroupReviewService dealGroupReviewService;
    private final AlertNotifier alertNotifier;
    private final TelegramAlertSettings telegramAlertSettings;

    public DealGroupController(
            DealGroupingService dealGroupingService,
            DealGroupReviewService dealGroupReviewService,
            AlertNotifier alertNotifier,
            TelegramAlertSettings telegramAlertSettings
    ) {
        this.dealGroupingService = dealGroupingService;
        this.dealGroupReviewService = dealGroupReviewService;
        this.alertNotifier = alertNotifier;
        this.telegramAlertSettings = telegramAlertSettings;
    }

    @GetMapping("/api/deal-groups")
    @Transactional(readOnly = true)
    public List<DealGroupingService.DealGroupResponse> dealGroups(
            @RequestParam(required = false) ManualReviewStatus reviewStatus,
            @RequestParam(required = false) UnifiedPriority priority,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return dealGroupingService.groups(reviewStatus, priority, limit);
    }

    @GetMapping(value = "/api/deal-groups/export.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportDealGroupsCsv(
            @RequestParam(required = false) ManualReviewStatus reviewStatus,
            @RequestParam(required = false) UnifiedPriority priority,
            @RequestParam(defaultValue = "500") int limit
    ) {
        List<DealGroupingService.DealGroupResponse> groups = dealGroupingService.groups(
                reviewStatus,
                priority,
                normalizedExportLimit(limit)
        );
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parsernews-deal-groups.csv\"")
                .body(toCsv(groups));
    }

    @GetMapping("/api/deal-groups/{groupKey}")
    @Transactional(readOnly = true)
    public DealGroupingService.DealGroupResponse dealGroup(@PathVariable String groupKey) {
        return dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
    }

    @PatchMapping("/api/deal-groups/{groupKey}/manual-review")
    @Transactional
    public DealGroupingService.DealGroupResponse updateManualReview(
            @PathVariable String groupKey,
            @RequestBody DealGroupManualReviewRequest request
    ) {
        dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        DealGroupReviewEntity review = dealGroupReviewService.update(groupKey, request.status(), request.reason(), request.note());
        return dealGroupingService.group(review.getGroupKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
    }

    @GetMapping("/api/deal-groups/{groupKey}/telegram-preview")
    @Transactional(readOnly = true)
    public DealGroupTelegramPreviewResponse telegramPreview(@PathVariable String groupKey) {
        DealGroupingService.DealGroupResponse group = dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        return new DealGroupTelegramPreviewResponse(
                group.groupKey(),
                group.title(),
                dealGroupingService.formatTelegramPreview(group),
                group.reviewStatus(),
                group.reviewReason(),
                group.groupReviewStored()
        );
    }

    @PostMapping("/api/deal-groups/{groupKey}/send-telegram")
    @Transactional(readOnly = true)
    public DealGroupTelegramSendResponse sendTelegram(@PathVariable String groupKey) {
        DealGroupingService.DealGroupResponse group = dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        TelegramReadiness readiness = telegramReadiness();
        if (!readiness.sendAllowed()) {
            return new DealGroupTelegramSendResponse(
                    false,
                    readiness.telegramEnabled(),
                    readiness.telegramConfigured(),
                    readiness.reason(),
                    null
            );
        }
        AlertNotifier.AlertNotificationResult notification = alertNotifier.send(dealGroupingService.formatTelegramPreview(group));
        return new DealGroupTelegramSendResponse(
                notification.sent(),
                readiness.telegramEnabled(),
                readiness.telegramConfigured(),
                notification.reason(),
                notification.sent() ? null : notification.status()
        );
    }

    private TelegramReadiness telegramReadiness() {
        boolean enabled = telegramAlertSettings.enabled();
        boolean configured = !isBlank(telegramAlertSettings.botToken()) && !isBlank(telegramAlertSettings.chatId());
        if (!enabled) {
            return new TelegramReadiness(false, configured, false, "Telegram is disabled; no external message will be sent.");
        }
        if (!configured) {
            return new TelegramReadiness(true, false, false, "Telegram is enabled but bot token or chat id is missing.");
        }
        return new TelegramReadiness(true, true, true, null);
    }

    private String toCsv(List<DealGroupingService.DealGroupResponse> groups) {
        StringBuilder builder = new StringBuilder();
        appendCsvRow(builder, List.of(
                "groupKey",
                "title",
                "buyerCompany",
                "targetCompany",
                "buyerTicker",
                "targetTicker",
                "buyerCik",
                "targetCik",
                "priority",
                "dealRelevance",
                "tradability",
                "dealStage",
                "dealTiming",
                "reviewStatus",
                "reviewReason",
                "relatedSignalsCount",
                "evidenceUrls",
                "warnings"
        ));
        groups.forEach(group -> appendCsvRow(builder, Arrays.asList(
                group.groupKey(),
                group.title(),
                group.buyerCompany(),
                group.targetCompany(),
                group.buyerTicker(),
                group.targetTicker(),
                group.buyerCik(),
                group.targetCik(),
                group.priority(),
                group.dealRelevance(),
                group.tradability(),
                group.dealStage(),
                group.dealTiming(),
                group.reviewStatus(),
                group.reviewReason(),
                group.relatedSignals().size(),
                String.join(" | ", group.evidenceUrls()),
                String.join(" | ", group.warnings())
        )));
        return builder.toString();
    }

    private void appendCsvRow(StringBuilder builder, List<?> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(csvCell(values.get(index)));
        }
        builder.append('\n');
    }

    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        boolean needsQuotes = text.contains(",")
                || text.contains("\"")
                || text.contains("\n")
                || text.contains("\r");
        String escaped = text.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private int normalizedExportLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DealGroupManualReviewRequest(
            ManualReviewStatus status,
            ManualReviewReason reason,
            String note
    ) {
    }

    public record DealGroupTelegramPreviewResponse(
            String groupKey,
            String title,
            String messageText,
            ManualReviewStatus reviewStatus,
            ManualReviewReason reviewReason,
            boolean groupReviewStored
    ) {
    }

    public record DealGroupTelegramSendResponse(
            boolean sent,
            boolean telegramEnabled,
            boolean telegramConfigured,
            String message,
            String error
    ) {
    }

    private record TelegramReadiness(
            boolean telegramEnabled,
            boolean telegramConfigured,
            boolean sendAllowed,
            String reason
    ) {
    }
}
