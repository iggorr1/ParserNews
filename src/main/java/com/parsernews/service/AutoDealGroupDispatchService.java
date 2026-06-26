package com.parsernews.service;

import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.DealGroupReviewRepository;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AutoDealGroupDispatchService {
    // Verdicts that warrant automatic Telegram notification
    private static final Set<AiReviewVerdict> DISPATCH_VERDICTS = Set.of(
            AiReviewVerdict.GOOD_SIGNAL,
            AiReviewVerdict.NEEDS_HUMAN_REVIEW
    );

    private static final int MAX_AI_PER_RUN = 3;

    private final DealGroupingService dealGroupingService;
    private final DealGroupAiReviewService aiReviewService;
    private final DealGroupReviewService dealGroupReviewService;
    private final DealGroupReviewRepository reviewRepository;
    private final AlertNotifier alertNotifier;
    private final StockPriceService stockPriceService;
    private final OpenAiRuntimeSettingsService openAiSettings;
    private final TelegramRuntimeSettingsService telegramSettings;

    public AutoDealGroupDispatchService(
            DealGroupingService dealGroupingService,
            DealGroupAiReviewService aiReviewService,
            DealGroupReviewService dealGroupReviewService,
            DealGroupReviewRepository reviewRepository,
            AlertNotifier alertNotifier,
            StockPriceService stockPriceService,
            OpenAiRuntimeSettingsService openAiSettings,
            TelegramRuntimeSettingsService telegramSettings
    ) {
        this.dealGroupingService = dealGroupingService;
        this.aiReviewService = aiReviewService;
        this.dealGroupReviewService = dealGroupReviewService;
        this.reviewRepository = reviewRepository;
        this.alertNotifier = alertNotifier;
        this.stockPriceService = stockPriceService;
        this.openAiSettings = openAiSettings;
        this.telegramSettings = telegramSettings;
    }

    /**
     * Triggered immediately after each scan completes (event-driven, async).
     * Also runs on a fallback schedule every 2 minutes in case events are missed.
     */
    @EventListener
    @Async
    public void onScanCompleted(ScanCompletedEvent event) {
        autoDispatch();
    }

    @Scheduled(fixedDelayString = "${auto.dispatch.fixed-delay-ms:120000}",
               initialDelayString = "${auto.dispatch.initial-delay-ms:120000}")
    public void scheduledDispatch() {
        autoDispatch();
    }

    @Transactional
    public void autoDispatch() {
        if (!openAiSettings.effectiveSettings().enabled() || !openAiSettings.effectiveSettings().configured()) {
            return;
        }
        if (!telegramSettings.effectiveSettings().enabled() || !telegramSettings.effectiveSettings().sendAllowed()) {
            return;
        }

        // Get all HIGH+MEDIUM PENDING groups not yet dispatched to Telegram
        List<DealGroupReviewEntity> notDispatched = reviewRepository.findByTgDispatchedAtIsNull();
        List<String> notDispatchedKeys = notDispatched.stream()
                .filter(r -> r.getManualReviewStatus() == ManualReviewStatus.PENDING)
                .map(DealGroupReviewEntity::getGroupKey)
                .toList();

        // Also pick up groups with no review record yet (brand new)
        List<DealGroupingService.DealGroupResponse> candidates = dealGroupingService
                .groups(ManualReviewStatus.PENDING, null, 50)
                .stream()
                .filter(g -> g.priority() == UnifiedPriority.HIGH || g.priority() == UnifiedPriority.MEDIUM)
                .filter(g -> notDispatchedKeys.contains(g.groupKey()) || g.reviewStatus() == ManualReviewStatus.PENDING)
                .toList();

        int aiRan = 0;
        for (DealGroupingService.DealGroupResponse group : candidates) {
            if (alreadyDispatched(group.groupKey())) continue;

            // Run AI review if not done yet and budget allows
            DealGroupAiReviewService.AiReviewResponse aiReview = aiReviewService.latest(group.groupKey());
            boolean hasReview = aiReview.verdict() != null && aiReview.verdict() != AiReviewVerdict.UNKNOWN;

            if (!hasReview && aiRan < MAX_AI_PER_RUN) {
                try {
                    aiReview = aiReviewService.review(group.groupKey());
                    aiRan++;
                } catch (Exception e) {
                    continue;
                }
            }

            if (aiReview.verdict() == null || !DISPATCH_VERDICTS.contains(aiReview.verdict())) {
                continue;
            }

            // Re-fetch group after AI review
            DealGroupingService.DealGroupResponse fresh = dealGroupingService.group(group.groupKey()).orElse(group);
            dispatchToTelegram(fresh);
        }
    }

    private boolean alreadyDispatched(String groupKey) {
        return reviewRepository.findByGroupKey(groupKey)
                .map(DealGroupReviewEntity::isTgDispatched)
                .orElse(false);
    }

    private void dispatchToTelegram(DealGroupingService.DealGroupResponse group) {
        DealGroupAiReviewService.AiReviewResponse aiReview = aiReviewService.latest(group.groupKey());
        String message = dealGroupingService.formatTelegramPreview(group)
                + formatAiSection(aiReview)
                + formatPriceSection(group.targetTicker());
        List<AlertNotifier.InlineButton> buttons = List.of(
                AlertNotifier.InlineButton.callback("✓ Useful", "qr|USEFUL|" + group.groupKey()),
                AlertNotifier.InlineButton.callback("✗ Ignore", "qr|IGNORED|" + group.groupKey())
        );
        AlertNotifier.AlertNotificationResult result = alertNotifier.sendWithButtons(message, buttons);
        if (result.sent()) {
            DealGroupReviewEntity review = dealGroupReviewService.getOrCreate(group.groupKey());
            review.markTgDispatched();
        }
    }

    private String formatAiSection(DealGroupAiReviewService.AiReviewResponse ai) {
        if (ai == null || ai.verdict() == null) return "";
        StringBuilder b = new StringBuilder("\n\n🤖 <b>AI:</b> ").append(ai.verdict());
        if (ai.confidence() != null) b.append(" · ").append(ai.confidence()).append(" confidence");
        if (ai.reason() != null && !ai.reason().isBlank()) {
            String reason = ai.reason().length() > 300 ? ai.reason().substring(0, 297) + "…" : ai.reason();
            b.append('\n').append("<i>").append(reason).append("</i>");
        }
        if (ai.riskFlags() != null && !ai.riskFlags().isEmpty()) {
            b.append('\n').append("⚠️ <b>Risks:</b> ").append(String.join(", ", ai.riskFlags()));
        }
        return b.toString();
    }

    private String formatPriceSection(String ticker) {
        if (ticker == null || ticker.isBlank() || "UNKNOWN".equalsIgnoreCase(ticker)) return "";
        return stockPriceService.currentPrice(ticker)
                .map(p -> "\n\n💰 <b>Price:</b> " + p.formatted() + " | " + p.shortName())
                .orElse("");
    }
}
