package com.parsernews.model;

import java.util.List;

public record AnalysisResult(
        EventType eventType,
        EventStatus status,
        int score,
        List<String> matchedPositiveKeywords,
        List<String> matchedNegativeKeywords,
        String reason
) {
}
