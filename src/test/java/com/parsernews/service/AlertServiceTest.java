package com.parsernews.service;

import com.parsernews.model.AnalysisResult;
import com.parsernews.model.EventStatus;
import com.parsernews.model.EventType;
import com.parsernews.model.NewsEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertServiceTest {
    private final AlertService alertService = new AlertService();

    @Test
    void matchesExpectedWhenBothLabelsMatch() {
        NewsEvent event = labeledNews(EventType.TAKE_PRIVATE_CONFIRMED, EventStatus.IMPORTANT);
        AnalysisResult result = result(EventType.TAKE_PRIVATE_CONFIRMED, EventStatus.IMPORTANT);

        assertThat(alertService.matchesExpected(event, result)).isTrue();
    }

    @Test
    void doesNotMatchExpectedWhenStatusDiffers() {
        NewsEvent event = labeledNews(EventType.TAKE_PRIVATE_CONFIRMED, EventStatus.IMPORTANT);
        AnalysisResult result = result(EventType.TAKE_PRIVATE_CONFIRMED, EventStatus.WATCHLIST);

        assertThat(alertService.matchesExpected(event, result)).isFalse();
    }

    private NewsEvent labeledNews(EventType eventType, EventStatus status) {
        return new NewsEvent(
                "TEST",
                "Test Company",
                "Headline",
                "Body",
                "Test Source",
                "https://example.com/test",
                eventType,
                status,
                "Test note"
        );
    }

    private AnalysisResult result(EventType eventType, EventStatus status) {
        return new AnalysisResult(eventType, status, 100, List.of(), List.of(), "Test reason");
    }
}
