package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.DealGroupReviewRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AutoDealGroupDispatchService {
    private static final Logger log = LoggerFactory.getLogger(AutoDealGroupDispatchService.class);
    // Verdicts that warrant automatic Telegram notification
    private static final Set<AiReviewVerdict> DISPATCH_VERDICTS = Set.of(
            AiReviewVerdict.GOOD_SIGNAL,
            AiReviewVerdict.NEEDS_HUMAN_REVIEW
    );

    // Terminal negative verdicts — auto-mark IGNORED so they never re-appear in dispatch runs
    private static final Set<AiReviewVerdict> TERMINAL_VERDICTS = Set.of(
            AiReviewVerdict.PRIVATE_COMPANY,
            AiReviewVerdict.NOT_TRADABLE,
            AiReviewVerdict.LATE_STAGE_UPDATE,
            AiReviewVerdict.FALSE_POSITIVE,
            AiReviewVerdict.DUPLICATE_OR_UPDATE
    );

    // Max AI reviews per dispatch run. Caps OpenAI cost and per-run duration, but too low a
    // value starves throughput during a burst of deals (extras wait for the next poll cycle).
    private final int maxAiPerRun;
    private static final double MIN_PRICE_USD = 0.50;

    private final AtomicBoolean running = new AtomicBoolean(false);

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
            TelegramRuntimeSettingsService telegramSettings,
            @org.springframework.beans.factory.annotation.Value("${auto.dispatch.max-ai-per-run:5}") int maxAiPerRun
    ) {
        this.maxAiPerRun = maxAiPerRun;
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

    public void autoDispatch() {
        // Prevent concurrent runs (event + schedule firing together)
        if (!running.compareAndSet(false, true)) return;
        try {
            doDispatch();
        } finally {
            running.set(false);
        }
    }

    private void doDispatch() {
        if (!openAiSettings.effectiveSettings().enabled() || !openAiSettings.effectiveSettings().configured()) {
            log.warn("Dispatch skipped: OpenAI not enabled/configured");
            return;
        }
        if (!telegramSettings.effectiveSettings().enabled() || !telegramSettings.effectiveSettings().sendAllowed()) {
            log.warn("Dispatch skipped: Telegram not enabled/configured");
            return;
        }

        List<String> notDispatchedKeys = findNotDispatchedPendingKeys();

        List<DealGroupingService.DealGroupResponse> candidates = dealGroupingService
                .groups(ManualReviewStatus.PENDING, null, 50)
                .stream()
                .filter(g -> g.priority() == UnifiedPriority.HIGH || g.priority() == UnifiedPriority.MEDIUM)
                .filter(g -> notDispatchedKeys.contains(g.groupKey()) || !g.groupReviewStored())
                .filter(this::passesQualityGate)
                .toList();

        if (candidates.isEmpty()) return;
        log.info("Dispatch run: {} candidate(s) after quality gate", candidates.size());

        int aiRan = 0, dispatched = 0, autoIgnored = 0, pending = 0;
        for (DealGroupingService.DealGroupResponse group : candidates) {
            if (alreadyDispatched(group.groupKey())) continue;

            DealGroupAiReviewService.AiReviewResponse aiReview = aiReviewService.latest(group.groupKey());
            boolean hasReview = aiReview.verdict() != null && aiReview.verdict() != AiReviewVerdict.UNKNOWN;

            if (!hasReview && aiRan < maxAiPerRun) {
                try {
                    aiReview = aiReviewService.review(group.groupKey());
                    aiRan++;
                } catch (Exception e) {
                    log.warn("AI review failed for {}: {}", group.groupKey(), e.getMessage());
                    continue;
                }
            }

            AiReviewVerdict verdict = aiReview.verdict();
            if (verdict == null || !DISPATCH_VERDICTS.contains(verdict)) {
                log.debug("Skipping {} — AI verdict: {}", group.groupKey(), verdict);
                if (TERMINAL_VERDICTS.contains(verdict)) {
                    dealGroupReviewService.update(group.groupKey(), ManualReviewStatus.IGNORED,
                            toReason(verdict), "Auto-ignored by dispatch: AI verdict " + verdict);
                    autoIgnored++;
                } else {
                    pending++;
                }
                continue;
            }

            DealGroupingService.DealGroupResponse fresh = dealGroupingService.group(group.groupKey()).orElse(group);
            dispatchToTelegram(fresh);
            dispatched++;
        }
        log.info("Dispatch done — sent: {}, auto-ignored: {}, pending AI: {}", dispatched, autoIgnored, pending);
    }

    @Transactional(readOnly = true)
    protected List<String> findNotDispatchedPendingKeys() {
        return reviewRepository.findByTgDispatchedAtIsNull().stream()
                .filter(r -> r.getManualReviewStatus() == ManualReviewStatus.PENDING)
                .map(DealGroupReviewEntity::getGroupKey)
                .toList();
    }

    private boolean passesQualityGate(DealGroupingService.DealGroupResponse group) {
        // HIGH-priority groups carry a strong M&A signal (a clear "to be acquired" headline, or a
        // SEC tender-offer/going-private filing that has no RSS-derived relevance at all). Let those
        // through to AI review — the AI verdict is the real filter — instead of dropping them here.
        // Only apply the deterministic relevance/tradability gate to lower-priority candidates.
        boolean highPriority = group.priority() == UnifiedPriority.HIGH;
        boolean unknownRelevance = group.dealRelevance() == null || group.dealRelevance() == DealRelevance.UNKNOWN;
        if (!highPriority && unknownRelevance && group.tradability() != com.parsernews.model.Tradability.HIGH) {
            return false;
        }
        // Skip penny stocks (price < $0.50) when ticker is known
        String ticker = group.targetTicker();
        if (ticker != null && !ticker.isBlank() && !"UNKNOWN".equalsIgnoreCase(ticker)) {
            return stockPriceService.currentPrice(ticker)
                    .map(p -> p.price() >= MIN_PRICE_USD)
                    .orElse(true); // if price unavailable, don't block
        }
        return true;
    }

    @Transactional(readOnly = true)
    protected boolean alreadyDispatched(String groupKey) {
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
        // Always mark as dispatched to prevent infinite retry loops.
        // If send failed, log the reason — but don't keep retrying on every scan cycle.
        dealGroupReviewService.markTgDispatched(group.groupKey());
        if (result.sent()) {
            log.info("Dispatched to Telegram: {} ({})", group.groupKey(), group.targetTicker());
        } else {
            log.warn("Telegram send failed for {} [{}]: {}", group.groupKey(), result.status(), result.reason());
        }
    }

    private String formatAiSection(DealGroupAiReviewService.AiReviewResponse ai) {
        if (ai == null || ai.verdict() == null) return "";
        StringBuilder b = new StringBuilder("\n\n🤖 <b>AI:</b> ").append(ai.verdict());
        if (ai.confidence() != null) b.append(" · ").append(ai.confidence()).append(" confidence");
        if (ai.reason() != null && !ai.reason().isBlank()) {
            String reason = ai.reason().length() > 300 ? ai.reason().substring(0, 297) + "…" : ai.reason();
            b.append('\n').append("<i>").append(esc(reason)).append("</i>");
        }
        if (ai.riskFlags() != null && !ai.riskFlags().isEmpty()) {
            String flags = ai.riskFlags().stream().map(AutoDealGroupDispatchService::esc)
                    .collect(java.util.stream.Collectors.joining(", "));
            b.append('\n').append("⚠️ <b>Risks:</b> ").append(flags);
        }
        return b.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static ManualReviewReason toReason(AiReviewVerdict verdict) {
        return switch (verdict) {
            case PRIVATE_COMPANY -> ManualReviewReason.PRIVATE_COMPANY;
            case NOT_TRADABLE -> ManualReviewReason.NOT_TRADABLE;
            case LATE_STAGE_UPDATE -> ManualReviewReason.LATE_STAGE_UPDATE;
            case FALSE_POSITIVE -> ManualReviewReason.FALSE_POSITIVE;
            case DUPLICATE_OR_UPDATE -> ManualReviewReason.DUPLICATE_OR_UPDATE;
            default -> ManualReviewReason.OTHER;
        };
    }

    private String formatPriceSection(String ticker) {
        if (ticker == null || ticker.isBlank() || "UNKNOWN".equalsIgnoreCase(ticker)) return "";
        return stockPriceService.currentPrice(ticker)
                .map(p -> "\n\n💰 <b>Price:</b> " + esc(p.formatted()) + " | " + esc(p.shortName()))
                .orElse("");
    }
}
