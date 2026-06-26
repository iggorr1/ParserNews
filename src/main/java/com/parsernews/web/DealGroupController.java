package com.parsernews.web;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.AlertNotifier;
import com.parsernews.service.DealGroupAiReviewService;
import com.parsernews.service.DealGroupReviewService;
import com.parsernews.service.DealGroupingService;
import com.parsernews.service.TelegramRuntimeSettingsService;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DealGroupController {
    private final DealGroupingService dealGroupingService;
    private final DealGroupReviewService dealGroupReviewService;
    private final DealGroupAiReviewService dealGroupAiReviewService;
    private final AlertNotifier alertNotifier;
    private final TelegramRuntimeSettingsService telegramRuntimeSettingsService;
    private final String appBaseUrl;

    public DealGroupController(
            DealGroupingService dealGroupingService,
            DealGroupReviewService dealGroupReviewService,
            DealGroupAiReviewService dealGroupAiReviewService,
            AlertNotifier alertNotifier,
            TelegramRuntimeSettingsService telegramRuntimeSettingsService,
            @Value("${app.base-url:}") String appBaseUrl
    ) {
        this.dealGroupingService = dealGroupingService;
        this.dealGroupReviewService = dealGroupReviewService;
        this.dealGroupAiReviewService = dealGroupAiReviewService;
        this.alertNotifier = alertNotifier;
        this.telegramRuntimeSettingsService = telegramRuntimeSettingsService;
        this.appBaseUrl = appBaseUrl == null ? "" : appBaseUrl.stripTrailing();
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

    @GetMapping("/api/deal-groups/stats")
    @Transactional(readOnly = true)
    public DealGroupStatsResponse dealGroupStats() {
        List<DealGroupingService.DealGroupResponse> groups = allGroupsForStats();
        long groupedEvidenceTotal = groups.stream()
                .mapToLong(group -> group.relatedSignals().size())
                .sum();
        double averageEvidencePerGroup = groups.isEmpty()
                ? 0.0
                : (double) groupedEvidenceTotal / groups.size();
        return new DealGroupStatsResponse(
                groups.size(),
                countReviewStatus(groups, ManualReviewStatus.PENDING),
                countReviewStatus(groups, ManualReviewStatus.USEFUL),
                countReviewStatus(groups, ManualReviewStatus.IGNORED),
                countPriority(groups, UnifiedPriority.HIGH),
                groups.stream().filter(this::isAlertLikeGroup).count(),
                groups.stream().filter(this::isSecOnlyGroup).count(),
                groupedEvidenceTotal,
                averageEvidencePerGroup,
                reasonBreakdown(groups),
                dealRelevanceBreakdown(groups),
                tradabilityBreakdown(groups),
                dealStageBreakdown(groups),
                dealTimingBreakdown(groups),
                priorityBreakdown(groups)
        );
    }

    @GetMapping("/api/deal-groups/ai-review/summary")
    @Transactional(readOnly = true)
    public DealGroupAiReviewService.AiReviewSummaryResponse aiReviewSummary() {
        return dealGroupAiReviewService.summary();
    }

    @PostMapping("/api/deal-groups/ai-review/batch")
    @Transactional
    public DealGroupAiReviewService.BatchAiReviewResponse batchAiReview(
            @RequestBody(required = false) DealGroupAiReviewService.BatchAiReviewRequest request
    ) {
        return dealGroupAiReviewService.batchReview(request);
    }

    @GetMapping("/api/deal-groups/ai-review/batch-candidates")
    @Transactional(readOnly = true)
    public DealGroupAiReviewService.BatchCandidatePreviewResponse batchAiReviewCandidates(
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "true") boolean skipAlreadyReviewed,
            @RequestParam(defaultValue = "MEDIUM") UnifiedPriority minPriority,
            @RequestParam(defaultValue = "true") boolean onlyPromising
    ) {
        return dealGroupAiReviewService.previewBatchCandidates(new DealGroupAiReviewService.BatchCandidatePreviewRequest(
                limit,
                skipAlreadyReviewed,
                minPriority,
                onlyPromising
        ));
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
        List<AlertNotifier.InlineButton> buttons = buildQuickReviewButtons(group.groupKey());
        AlertNotifier.AlertNotificationResult notification = alertNotifier.sendWithButtons(
                dealGroupingService.formatTelegramPreview(group), buttons);
        return new DealGroupTelegramSendResponse(
                notification.sent(),
                readiness.telegramEnabled(),
                readiness.telegramConfigured(),
                notification.reason(),
                notification.sent() ? null : notification.status()
        );
    }

    @PostMapping("/api/deal-groups/{groupKey}/quick-review")
    @Transactional
    public ResponseEntity<Void> quickReview(
            @PathVariable String groupKey,
            @RequestParam ManualReviewStatus status
    ) {
        dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        dealGroupReviewService.update(groupKey, status, null, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/deal-groups/{groupKey}/ai-review/latest")
    @Transactional(readOnly = true)
    public DealGroupAiReviewService.AiReviewResponse latestAiReview(@PathVariable String groupKey) {
        dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        return dealGroupAiReviewService.latest(groupKey);
    }

    @PostMapping("/api/deal-groups/{groupKey}/ai-review")
    @Transactional
    public DealGroupAiReviewService.AiReviewResponse aiReview(@PathVariable String groupKey) {
        dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found"));
        return dealGroupAiReviewService.review(groupKey);
    }

    private List<AlertNotifier.InlineButton> buildQuickReviewButtons(String groupKey) {
        if (appBaseUrl.isBlank()) {
            return List.of();
        }
        String base = appBaseUrl + "/api/deal-groups/" + groupKey + "/quick-review";
        return List.of(
                new AlertNotifier.InlineButton("✓ Useful", base + "?status=USEFUL"),
                new AlertNotifier.InlineButton("✗ Ignore", base + "?status=IGNORED")
        );
    }

    private TelegramReadiness telegramReadiness() {
        TelegramRuntimeSettingsService.EffectiveTelegramSettings settings = telegramRuntimeSettingsService.effectiveSettings();
        return new TelegramReadiness(
                settings.enabled(),
                settings.configured(),
                settings.sendAllowed(),
                settings.sendAllowed() ? null : settings.message()
        );
    }

    private List<DealGroupingService.DealGroupResponse> allGroupsForStats() {
        Map<String, DealGroupingService.DealGroupResponse> groupsByKey = new LinkedHashMap<>();
        for (ManualReviewStatus status : ManualReviewStatus.values()) {
            dealGroupingService.groups(status, null, 200)
                    .forEach(group -> groupsByKey.put(group.groupKey(), group));
        }
        return List.copyOf(groupsByKey.values());
    }

    private boolean isSecOnlyGroup(DealGroupingService.DealGroupResponse group) {
        return !group.relatedSignals().isEmpty()
                && group.relatedSignals().stream()
                        .allMatch(signal -> signal.sourceType() == SignalInboxController.SourceType.SEC_FILING);
    }

    private boolean isAlertLikeGroup(DealGroupingService.DealGroupResponse group) {
        return group.warnings().stream()
                .anyMatch(warning -> "RSS signal is alert eligible".equalsIgnoreCase(warning))
                || (group.priority() == UnifiedPriority.HIGH
                && (group.dealRelevance() == DealRelevance.PUBLIC_TAKE_PRIVATE
                || group.dealRelevance() == DealRelevance.PUBLIC_CASH_ACQUISITION)
                && (group.tradability() == Tradability.HIGH || group.tradability() == Tradability.MEDIUM)
                && group.dealTiming() != DealTiming.LATE_STAGE
                && group.dealTiming() != DealTiming.POST_CLOSE
                && group.dealTiming() != DealTiming.NOISE);
    }

    private long countReviewStatus(List<DealGroupingService.DealGroupResponse> groups, ManualReviewStatus status) {
        return groups.stream().filter(group -> group.reviewStatus() == status).count();
    }

    private long countPriority(List<DealGroupingService.DealGroupResponse> groups, UnifiedPriority priority) {
        return groups.stream().filter(group -> group.priority() == priority).count();
    }

    private Map<String, Long> reasonBreakdown(List<DealGroupingService.DealGroupResponse> groups) {
        Map<String, Long> counts = enumMap(ManualReviewReason.values());
        groups.stream()
                .map(DealGroupingService.DealGroupResponse::reviewReason)
                .filter(reason -> reason != null)
                .forEach(reason -> counts.compute(reason.name(), (key, count) -> count == null ? 1 : count + 1));
        return counts;
    }

    private Map<String, Long> dealRelevanceBreakdown(List<DealGroupingService.DealGroupResponse> groups) {
        Map<String, Long> counts = enumMap(DealRelevance.values());
        groups.stream()
                .map(DealGroupingService.DealGroupResponse::dealRelevance)
                .filter(value -> value != null)
                .forEach(value -> counts.compute(value.name(), (key, count) -> count == null ? 1 : count + 1));
        return counts;
    }

    private Map<String, Long> tradabilityBreakdown(List<DealGroupingService.DealGroupResponse> groups) {
        Map<String, Long> counts = enumMap(Tradability.values());
        groups.stream()
                .map(DealGroupingService.DealGroupResponse::tradability)
                .filter(value -> value != null)
                .forEach(value -> counts.compute(value.name(), (key, count) -> count == null ? 1 : count + 1));
        return counts;
    }

    private Map<String, Long> dealStageBreakdown(List<DealGroupingService.DealGroupResponse> groups) {
        Map<String, Long> counts = enumMap(DealStage.values());
        groups.stream()
                .map(DealGroupingService.DealGroupResponse::dealStage)
                .filter(value -> value != null)
                .forEach(value -> counts.compute(value.name(), (key, count) -> count == null ? 1 : count + 1));
        return counts;
    }

    private Map<String, Long> dealTimingBreakdown(List<DealGroupingService.DealGroupResponse> groups) {
        Map<String, Long> counts = enumMap(DealTiming.values());
        groups.stream()
                .map(DealGroupingService.DealGroupResponse::dealTiming)
                .filter(value -> value != null)
                .forEach(value -> counts.compute(value.name(), (key, count) -> count == null ? 1 : count + 1));
        return counts;
    }

    private Map<String, Long> priorityBreakdown(List<DealGroupingService.DealGroupResponse> groups) {
        Map<String, Long> counts = enumMap(UnifiedPriority.values());
        groups.stream()
                .map(DealGroupingService.DealGroupResponse::priority)
                .filter(value -> value != null)
                .forEach(value -> counts.compute(value.name(), (key, count) -> count == null ? 1 : count + 1));
        return counts;
    }

    private Map<String, Long> enumMap(Enum<?>[] values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Arrays.stream(values).forEach(value -> counts.put(value.name(), 0L));
        return counts;
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

    public record DealGroupStatsResponse(
            long totalGroups,
            long pendingGroups,
            long usefulGroups,
            long ignoredGroups,
            long highPriorityGroups,
            long alertLikeGroups,
            long secOnlyGroups,
            long groupedEvidenceTotal,
            double averageEvidencePerGroup,
            Map<String, Long> reviewReasonBreakdown,
            Map<String, Long> byDealRelevance,
            Map<String, Long> byTradability,
            Map<String, Long> byDealStage,
            Map<String, Long> byDealTiming,
            Map<String, Long> byPriority
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
