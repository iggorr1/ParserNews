package com.parsernews.model;

import java.time.Instant;

public record NewsEvent(
        String ticker,
        String companyName,
        String headline,
        String body,
        String source,
        String sourceUrl,
        Instant publishedAt,
        EventType expectedEventType,
        EventStatus expectedStatus,
        String notes
) {
    public NewsEvent(
            String ticker,
            String companyName,
            String headline,
            String body,
            String source,
            String sourceUrl
    ) {
        this(ticker, companyName, headline, body, source, sourceUrl, null, null, null, null);
    }

    public NewsEvent(
            String ticker,
            String companyName,
            String headline,
            String body,
            String source,
            String sourceUrl,
            EventType expectedEventType,
            EventStatus expectedStatus,
            String notes
    ) {
        this(ticker, companyName, headline, body, source, sourceUrl, null, expectedEventType, expectedStatus, notes);
    }

    public String fullText() {
        return headline + " " + body;
    }

    public boolean hasExpectedResult() {
        return expectedEventType != null || expectedStatus != null;
    }
}
