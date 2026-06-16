package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.NewsEvent;
import org.springframework.stereotype.Service;

@Service
public class AlertService {
    public void printAlert(NewsEvent event, AnalysisResult result) {
        System.out.println("============================================================");
        System.out.println("Status: " + result.status());
        System.out.println("Ticker: " + event.ticker());
        System.out.println("Company: " + event.companyName());
        System.out.println("Headline: " + event.headline());
        System.out.println("Source: " + event.source());
        System.out.println("Event type: " + result.eventType());
        System.out.println("Score: " + result.score());
        System.out.println("Positive keywords: " + result.matchedPositiveKeywords());
        System.out.println("Negative keywords: " + result.matchedNegativeKeywords());
        System.out.println("Reason: " + result.reason());
        if (event.hasExpectedResult()) {
            System.out.println("Expected event type: " + event.expectedEventType());
            System.out.println("Expected status: " + event.expectedStatus());
            System.out.println("Matches expected: " + matchesExpected(event, result));
        }
        if (event.notes() != null && !event.notes().isBlank()) {
            System.out.println("Notes: " + event.notes());
        }
        System.out.println("URL: " + event.sourceUrl());
    }

    private boolean matchesExpected(NewsEvent event, AnalysisResult result) {
        boolean eventTypeMatches = event.expectedEventType() == null
                || event.expectedEventType() == result.eventType();
        boolean statusMatches = event.expectedStatus() == null
                || event.expectedStatus() == result.status();
        return eventTypeMatches && statusMatches;
    }
}
