package com.parsernews.model;

import java.util.List;

public record AnalysisResult(
        EventType eventType,
        EventStatus status,
        int score,
        String targetTicker,
        String offerPrice,
        String cashOrStock,
        String acquirer,
        String premiumPercent,
        List<String> matchedPositiveKeywords,
        List<String> matchedNegativeKeywords,
        String reason
) {
    public AnalysisResult(
            EventType eventType,
            EventStatus status,
            int score,
            List<String> matchedPositiveKeywords,
            List<String> matchedNegativeKeywords,
            String reason
    ) {
        this(eventType, status, score, null, null, null, null, null,
                matchedPositiveKeywords, matchedNegativeKeywords, reason);
    }
}
