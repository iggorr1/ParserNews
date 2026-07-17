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
    // Deals whose news is older than this are stale — already priced in — so they are auto-ignored
    // (never sent to Telegram, and cleared out of the PENDING queue).
    private final int maxDealAgeDays;
    private static final double MIN_PRICE_USD = 0.50;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final DealGroupingService dealGroupingService;
    private final DealGroupAiReviewService aiReviewService;
    private final DealGroupReviewService dealGroupReviewService;
    private final DealGroupReviewRepository reviewRepository;
    private final AlertNotifier alertNotifier;
    private final StockPriceService stockPriceService;
    private final PriceChartImageService priceChartImageService;
    private final OpenAiRuntimeSettingsService openAiSettings;
    private final TelegramRuntimeSettingsService telegramSettings;

    public AutoDealGroupDispatchService(
            DealGroupingService dealGroupingService,
            DealGroupAiReviewService aiReviewService,
            DealGroupReviewService dealGroupReviewService,
            DealGroupReviewRepository reviewRepository,
            AlertNotifier alertNotifier,
            StockPriceService stockPriceService,
            PriceChartImageService priceChartImageService,
            OpenAiRuntimeSettingsService openAiSettings,
            TelegramRuntimeSettingsService telegramSettings,
            @org.springframework.beans.factory.annotation.Value("${auto.dispatch.max-ai-per-run:5}") int maxAiPerRun,
            @org.springframework.beans.factory.annotation.Value("${auto.dispatch.max-deal-age-days:14}") int maxDealAgeDays
    ) {
        this.maxAiPerRun = maxAiPerRun;
        this.maxDealAgeDays = maxDealAgeDays;
        this.dealGroupingService = dealGroupingService;
        this.aiReviewService = aiReviewService;
        this.dealGroupReviewService = dealGroupReviewService;
        this.reviewRepository = reviewRepository;
        this.alertNotifier = alertNotifier;
        this.stockPriceService = stockPriceService;
        this.priceChartImageService = priceChartImageService;
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

    /**
     * Periodically clears stale news out of the PENDING queue: any deal group whose announcement
     * is older than the freshness threshold is auto-ignored. This both cleans up the existing
     * backlog and keeps old news from lingering in the dashboard/pipeline views.
     */
    @Scheduled(fixedDelayString = "${auto.dispatch.stale-sweep-delay-ms:3600000}",
               initialDelayString = "${auto.dispatch.stale-sweep-initial-delay-ms:60000}")
    public void sweepStaleDeals() {
        java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(maxDealAgeDays));
        int swept = 0;
        for (DealGroupingService.DealGroupResponse group : dealGroupingService.groups(ManualReviewStatus.PENDING, null, 500)) {
            if (group.sortInstant() != null && group.sortInstant().isBefore(cutoff)) {
                dealGroupReviewService.update(group.groupKey(), ManualReviewStatus.IGNORED,
                        ManualReviewReason.OTHER,
                        "Auto-ignored: stale news (older than " + maxDealAgeDays + " days)");
                swept++;
            }
        }
        if (swept > 0) {
            log.info("Stale sweep — auto-ignored {} deal group(s) older than {} days", swept, maxDealAgeDays);
        }
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

        int aiRan = 0, dispatched = 0, autoIgnored = 0, pending = 0, staleIgnored = 0;
        java.time.Instant staleCutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(maxDealAgeDays));
        for (DealGroupingService.DealGroupResponse group : candidates) {
            if (alreadyDispatched(group.groupKey())) continue;

            // Freshness gate — stale announcements are already priced in. Auto-ignore them so they
            // don't reach Telegram and drop out of the PENDING queue (this also cleans up old news).
            if (group.sortInstant() != null && group.sortInstant().isBefore(staleCutoff)) {
                dealGroupReviewService.update(group.groupKey(), ManualReviewStatus.IGNORED,
                        ManualReviewReason.OTHER,
                        "Auto-ignored by dispatch: stale news (older than " + maxDealAgeDays + " days)");
                staleIgnored++;
                continue;
            }

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
        log.info("Dispatch done — sent: {}, auto-ignored: {}, stale-ignored: {}, pending AI: {}",
                dispatched, autoIgnored, staleIgnored, pending);
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

    /** Caption, chart image and inline buttons for a deal's Telegram alert. */
    public record AlertContent(String caption, byte[] chart, List<AlertNotifier.InlineButton> buttons) {
    }

    private AlertContent buildAlert(DealGroupingService.DealGroupResponse group, DealGroupAiReviewService.AiReviewResponse aiReview) {
        String message = dealGroupingService.formatTelegramPreview(group)
                + formatAiSection(aiReview)
                + formatPriceSection(group, aiReview);
        List<AlertNotifier.InlineButton> buttons = List.of(
                AlertNotifier.InlineButton.callback("✓ Useful", "qr|USEFUL|" + group.groupKey()),
                AlertNotifier.InlineButton.callback("✗ Ignore", "qr|IGNORED|" + group.groupKey()),
                AlertNotifier.InlineButton.callback("🔄 Price", "rp|" + group.groupKey())
        );
        return new AlertContent(message, renderChart(group, aiReview), buttons);
    }

    /**
     * Rebuilds a dispatched alert with a fresh price and edits the Telegram message in place, so the
     * shown price and chart are current. Called from the callback poller when the "🔄 Price" button
     * is pressed. Returns a short status for the callback toast.
     */
    @Transactional(readOnly = true)
    public String refreshPriceMessage(String groupKey, String chatId, long messageId, boolean isPhoto) {
        DealGroupingService.DealGroupResponse group = dealGroupingService.group(groupKey).orElse(null);
        if (group == null) {
            return "Deal not found";
        }
        AlertContent content = buildAlert(group, aiReviewService.latest(groupKey));
        // A per-second stamp guarantees the caption differs, so Telegram never rejects the edit as
        // "message is not modified" when the price hasn't moved.
        String stamp = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Kyiv"))
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String caption = content.caption() + "\n\n🔄 <i>Обновлено " + stamp + "</i>";
        AlertNotifier.AlertNotificationResult result = (isPhoto && content.chart() != null)
                ? alertNotifier.editPhotoWithButtons(chatId, messageId, content.chart(), caption, content.buttons())
                : alertNotifier.editTextWithButtons(chatId, messageId, caption, content.buttons());
        return result.sent() ? "Цена обновлена ✓" : "Не удалось обновить";
    }

    private void dispatchToTelegram(DealGroupingService.DealGroupResponse group) {
        DealGroupAiReviewService.AiReviewResponse aiReview = aiReviewService.latest(group.groupKey());
        AlertContent content = buildAlert(group, aiReview);
        String message = content.caption();
        List<AlertNotifier.InlineButton> buttons = content.buttons();
        // A chart makes the spread obvious at a glance; sendPhotoWithButtons falls back to text when
        // the chart can't be built (no ticker/history) or the caption is too long for a photo.
        byte[] chart = content.chart();
        AlertNotifier.AlertNotificationResult result = alertNotifier.sendPhotoWithButtons(chart, message, buttons);
        // Always mark as dispatched to prevent infinite retry loops.
        // If send failed, log the reason — but don't keep retrying on every scan cycle.
        dealGroupReviewService.markTgDispatched(group.groupKey());
        if (result.sent()) {
            log.info("Dispatched to Telegram: {} ({})", group.groupKey(), group.targetTicker());
        } else {
            log.warn("Telegram send failed for {} [{}]: {}", group.groupKey(), result.status(), result.reason());
        }
    }

    private byte[] renderChart(DealGroupingService.DealGroupResponse group, DealGroupAiReviewService.AiReviewResponse ai) {
        try {
            String ticker = group.targetTicker();
            if (ticker == null || ticker.isBlank() || "UNKNOWN".equalsIgnoreCase(ticker)) {
                return null;
            }
            java.math.BigDecimal offer;
            String priceStatus = ai == null ? null : ai.priceStatus();
            if (ai != null && ai.verifiedOfferPrice() != null
                    && ("VERIFIED".equals(priceStatus) || "CORRECTED".equals(priceStatus))) {
                offer = ai.verifiedOfferPrice();
            } else {
                offer = group.offerPrice() != null ? group.offerPrice() : (ai == null ? null : ai.offerPrice());
            }
            // Window the chart so the news moment is visible with room on both sides. Recent news
            // gets an intraday view; older deals a daily one.
            java.time.Instant news = group.sortInstant();
            long ageDays = news == null ? 5 : Math.max(0, java.time.Duration.between(news, java.time.Instant.now()).toDays());
            String range = ageDays <= 4 ? "5d" : ageDays <= 25 ? "1mo" : ageDays <= 80 ? "3mo" : "6mo";
            String interval = ageDays <= 4 ? "30m" : "1d";
            var history = stockPriceService.history(ticker, range, interval).orElse(null);
            return priceChartImageService.render(ticker, group.targetCompany(), history, news, offer, group.offerCurrency());
        } catch (RuntimeException exception) {
            log.warn("Chart build failed for {}: {}", group.groupKey(), exception.getMessage());
            return null;
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

    private String formatPriceSection(DealGroupingService.DealGroupResponse group, DealGroupAiReviewService.AiReviewResponse ai) {
        String ticker = group.targetTicker();
        if (ticker == null || ticker.isBlank() || "UNKNOWN".equalsIgnoreCase(ticker)) return "";
        String priceStatus = ai == null ? null : ai.priceStatus();
        // Prefer the AI-check verified/corrected price; else the deterministic deal-terms price; else
        // the first-pass AI-extracted price (SEC-sourced deals carry the price in the filing).
        java.math.BigDecimal offerPrice;
        if (ai != null && ai.verifiedOfferPrice() != null
                && ("VERIFIED".equals(priceStatus) || "CORRECTED".equals(priceStatus))) {
            offerPrice = ai.verifiedOfferPrice();
        } else {
            offerPrice = group.offerPrice() != null ? group.offerPrice() : (ai == null ? null : ai.offerPrice());
        }
        String check = formatPriceCheck(ai);
        return stockPriceService.currentPrice(ticker)
                .map(p -> "\n\n💰 <b>Price:</b> " + esc(p.formatted()) + " | " + esc(p.shortName())
                        + formatSpread(offerPrice, p.price()) + check)
                .orElse("");
    }

    /** Human-readable line for the price-verification pass, so a reader sees whether to trust the spread. */
    private String formatPriceCheck(DealGroupAiReviewService.AiReviewResponse ai) {
        if (ai == null || ai.priceStatus() == null) return "";
        String status = ai.priceStatus();
        String label = switch (status) {
            case "VERIFIED" -> "✅ Price check: verified";
            case "CORRECTED" -> "✏️ Price check: corrected to " + ai.verifiedOfferPrice();
            case "NOT_A_CASH_PRICE" -> "ℹ️ Price check: no fixed cash-per-share price";
            case "NO_EVIDENCE" -> "⚠️ Price check: no price found in evidence";
            default -> "⚠️ Price check: UNVERIFIED";
        };
        StringBuilder b = new StringBuilder("\n").append(label);
        if (ai.priceQuote() != null && !ai.priceQuote().isBlank()) {
            String q = ai.priceQuote().length() > 200 ? ai.priceQuote().substring(0, 197) + "…" : ai.priceQuote();
            b.append("\n<i>“").append(esc(q)).append("”</i>");
        }
        return b.toString();
    }

    /**
     * Merger-arbitrage spread: how much the offer price sits above the current market price,
     * i.e. the return remaining if the deal closes at the announced terms. Shown only when the
     * deal reported a per-share offer price.
     */
    private static String formatSpread(java.math.BigDecimal offerPrice, double currentPrice) {
        if (offerPrice == null || currentPrice <= 0) return "";
        double offer = offerPrice.doubleValue();
        double spreadPct = (offer - currentPrice) / currentPrice * 100.0;
        String sign = spreadPct >= 0 ? "+" : "";
        return String.format("\n📈 <b>Spread:</b> %s%.1f%% (offer %.2f vs %.2f)", sign, spreadPct, offer, currentPrice);
    }
}
