package com.parsernews.web;

import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.DealGroupAiReviewService;
import com.parsernews.service.DealGroupingService;
import com.parsernews.service.StockPriceService;
import com.parsernews.web.SignalInboxController.UnifiedPriority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only data export for an external consumer (e.g. an analytics tool a teammate builds).
 * Authenticated by a shared API key in the {@code X-API-Key} header — independent of the session
 * login — so a script can pull the deal feed with a single request. Returns a flat, analytics-ready
 * shape: target/ticker, offer price, current price, computed merger-arb spread, and the AI verdict.
 */
@RestController
public class ExportController {
    private final DealGroupingService dealGroupingService;
    private final DealGroupAiReviewService aiReviewService;
    private final StockPriceService stockPriceService;
    private final String apiKey;

    public ExportController(
            DealGroupingService dealGroupingService,
            DealGroupAiReviewService aiReviewService,
            StockPriceService stockPriceService,
            @Value("${export.api-key:}") String apiKey
    ) {
        this.dealGroupingService = dealGroupingService;
        this.aiReviewService = aiReviewService;
        this.stockPriceService = stockPriceService;
        this.apiKey = apiKey;
    }

    @GetMapping("/api/export/deals")
    public ExportResponse deals(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String minPriority,
            @RequestParam(defaultValue = "false") boolean withPrice
    ) {
        requireKey(key);
        UnifiedPriority floor = parsePriority(minPriority);
        int capped = Math.max(1, Math.min(limit, 500));

        List<ExportDeal> deals = new ArrayList<>();
        for (DealGroupingService.DealGroupResponse group :
                dealGroupingService.groups(ManualReviewStatus.PENDING, null, capped)) {
            if (floor != null && priorityRank(group.priority()) < priorityRank(floor)) {
                continue;
            }
            deals.add(toExportDeal(group, withPrice));
        }
        return new ExportResponse(Instant.now(), deals.size(), deals);
    }

    /**
     * Forces a fresh AI re-extraction of a single deal (re-identifies target/acquirer and re-resolves
     * their tickers via SEC), then returns the corrected deal. The teammate's analytics calls this
     * when it spots a bad ticker or a target/acquirer mismatch and wants us to try again.
     */
    @PostMapping("/api/export/deals/{groupKey}/recheck")
    public ExportDeal recheck(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @PathVariable String groupKey,
            @RequestParam(defaultValue = "false") boolean withPrice
    ) {
        requireKey(key);
        try {
            aiReviewService.review(groupKey);
        } catch (IllegalArgumentException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage());
        }
        DealGroupingService.DealGroupResponse group = dealGroupingService.group(groupKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal group not found: " + groupKey));
        return toExportDeal(group, withPrice);
    }

    private ExportDeal toExportDeal(DealGroupingService.DealGroupResponse group, boolean withPrice) {
        DealGroupAiReviewService.AiReviewResponse ai = aiReviewService.latest(group.groupKey());
        BigDecimal offerPrice = group.offerPrice() != null ? group.offerPrice()
                : (ai != null ? ai.offerPrice() : null);

        // Prefer the AI-identified parties and the SEC-resolved ticker over the grouping's values,
        // which are the ones that were reported wrong (swapped sides / wrong ticker).
        String targetCompany = firstNonBlank(ai == null ? null : ai.targetCompany(), group.targetCompany());
        String acquirerCompany = firstNonBlank(ai == null ? null : ai.acquirerCompany(), group.buyerCompany());
        String acquirerTicker = ai == null ? null : ai.acquirerTicker();
        String tickerConfidence = ai == null ? null : ai.tickerConfidence();
        String ticker = firstNonBlank(ai == null ? null : ai.targetTicker(), group.targetTicker());

        Double currentPrice = null;
        Double spreadPct = null;
        boolean realTicker = ticker != null && !ticker.isBlank() && !"UNKNOWN".equalsIgnoreCase(ticker);
        if (withPrice && realTicker) {
            var priced = stockPriceService.currentPrice(ticker);
            if (priced.isPresent()) {
                currentPrice = priced.get().price();
                if (offerPrice != null && currentPrice > 0) {
                    spreadPct = offerPrice.subtract(BigDecimal.valueOf(currentPrice))
                            .divide(BigDecimal.valueOf(currentPrice), 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();
                }
            }
        }

        return new ExportDeal(
                group.groupKey(),
                group.primarySignalSourceType() == null ? null : group.primarySignalSourceType().name(),
                group.title(),
                targetCompany,
                realTicker ? ticker : null,
                tickerConfidence,
                group.targetCik(),
                acquirerCompany,
                acquirerTicker,
                group.priority() == null ? null : group.priority().name(),
                group.dealRelevance() == null ? null : group.dealRelevance().name(),
                group.dealStage() == null ? null : group.dealStage().name(),
                group.dealTiming() == null ? null : group.dealTiming().name(),
                offerPrice,
                group.offerCurrency(),
                currentPrice,
                spreadPct,
                ai == null ? null : (ai.verdict() == null ? null : ai.verdict().name()),
                ai == null ? null : (ai.confidence() == null ? null : ai.confidence().name()),
                ai == null ? null : ai.reason(),
                group.reviewStatus() == null ? null : group.reviewStatus().name(),
                group.sortInstant(),
                group.evidenceUrls()
        );
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    private void requireKey(String provided) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Export API key is not configured.");
        }
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!java.security.MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid X-API-Key.");
        }
    }

    private static UnifiedPriority parsePriority(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UnifiedPriority.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown minPriority: " + value);
        }
    }

    private static int priorityRank(UnifiedPriority priority) {
        return switch (priority == null ? UnifiedPriority.NONE : priority) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case NONE -> 0;
        };
    }

    public record ExportResponse(Instant generatedAt, int count, List<ExportDeal> deals) {
    }

    public record ExportDeal(
            String groupKey,
            String source,
            String title,
            String targetCompany,
            String targetTicker,
            String tickerConfidence,
            String targetCik,
            String buyerCompany,
            String acquirerTicker,
            String priority,
            String dealRelevance,
            String dealStage,
            String dealTiming,
            BigDecimal offerPrice,
            String offerCurrency,
            Double currentPrice,
            Double spreadPct,
            String aiVerdict,
            String aiConfidence,
            String aiReason,
            String reviewStatus,
            Instant newsDate,
            List<String> evidenceUrls
    ) {
    }
}
