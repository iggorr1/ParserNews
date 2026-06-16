package com.parsernews.web;

import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class EventController {
    private final DetectedEventRepository eventRepository;

    public EventController(DetectedEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @GetMapping("/api/events")
    @Transactional(readOnly = true)
    public List<EventResponse> events(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) DetectedEventType type,
            @RequestParam(required = false) NewsSourceType sourceType
    ) {
        return eventRepository.findTop200ByOrderByDetectedAtDesc().stream()
                .filter(event -> status == null || event.getReviewStatus() == status)
                .filter(event -> type == null || event.getEventType() == type)
                .filter(event -> sourceType == null || event.getArticle().getSource().getType() == sourceType)
                .map(EventResponse::from)
                .toList();
    }

    public record EventResponse(
            Long id,
            DetectedEventType eventType,
            ReviewStatus reviewStatus,
            int confidenceScore,
            String targetCompany,
            String targetTicker,
            String headline,
            String source,
            NewsSourceType sourceType,
            String sourceUrl,
            Instant publishedAt,
            Instant detectedAt,
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            String falsePositiveReasons,
            String explanation
    ) {
        static EventResponse from(DetectedEventEntity event) {
            return new EventResponse(
                    event.getId(),
                    event.getEventType(),
                    event.getReviewStatus(),
                    event.getConfidenceScore(),
                    event.getTargetCompany(),
                    event.getTargetTicker(),
                    event.getArticle().getHeadline(),
                    event.getArticle().getSource().getName(),
                    event.getArticle().getSource().getType(),
                    event.getArticle().getUrl(),
                    event.getArticle().getPublishedAt(),
                    event.getDetectedAt(),
                    event.getMatchedPositiveKeywords(),
                    event.getMatchedNegativeKeywords(),
                    event.getFalsePositiveReasons(),
                    event.getExplanation()
            );
        }
    }
}
