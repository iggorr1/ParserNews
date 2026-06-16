package com.parsernews.model;

import java.util.List;

public record ScanResult(
        String ticker,
        String companyName,
        String headline,
        String source,
        String sourceUrl,
        EventType eventType,
        EventStatus status,
        int score,
        String offerPrice,
        String cashOrStock,
        String acquirer,
        String premiumPercent,
        List<String> matchedPositiveKeywords,
        List<String> matchedNegativeKeywords,
        EventType expectedEventType,
        EventStatus expectedStatus,
        Boolean matchesExpected,
        String notes,
        String reason
) {
    public static ScanResult from(NewsEvent event, AnalysisResult result, Boolean matchesExpected) {
        return new ScanResult(
                event.ticker(),
                event.companyName(),
                event.headline(),
                event.source(),
                event.sourceUrl(),
                result.eventType(),
                result.status(),
                result.score(),
                result.offerPrice(),
                result.cashOrStock(),
                result.acquirer(),
                result.premiumPercent(),
                result.matchedPositiveKeywords(),
                result.matchedNegativeKeywords(),
                event.expectedEventType(),
                event.expectedStatus(),
                matchesExpected,
                event.notes(),
                result.reason()
        );
    }
}
