package com.parsernews.model;

public record NewsEvent(
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
    public NewsEvent(
            String ticker,
            String companyName,
            String headline,
            String body,
            String source,
            String sourceUrl
    ) {
        this(ticker, companyName, headline, body, source, sourceUrl, null, null, null);
    }

    public String fullText() {
        return headline + " " + body;
    }

    public boolean hasExpectedResult() {
        return expectedEventType != null || expectedStatus != null;
    }
}
