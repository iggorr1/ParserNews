package com.parsernews.web;

import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.persistence.ValidationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    @PatchMapping("/api/events/{id}/review")
    @Transactional
    public EventResponse updateReview(@PathVariable Long id, @RequestBody EventReviewRequest request) {
        DetectedEventEntity event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.updateReview(request.targetTicker(), request.validationStatus(), request.reviewNotes());
        return EventResponse.from(event);
    }

    public record EventResponse(
            Long id,
            DetectedEventType eventType,
            ReviewStatus reviewStatus,
            int confidenceScore,
            ValidationStatus validationStatus,
            String targetCompany,
            String targetTicker,
            String acquirer,
            String offerPrice,
            String cashOrStock,
            String premiumPercent,
            String headline,
            String source,
            NewsSourceType sourceType,
            String sourceUrl,
            Instant publishedAt,
            Instant detectedAt,
            String matchedPositiveKeywords,
            String matchedNegativeKeywords,
            String falsePositiveReasons,
            String explanation,
            String reviewNotes
    ) {
        static EventResponse from(DetectedEventEntity event) {
            return new EventResponse(
                    event.getId(),
                    event.getEventType(),
                    event.getReviewStatus(),
                    event.getConfidenceScore(),
                    event.getValidationStatus(),
                    event.getTargetCompany(),
                    event.getTargetTicker(),
                    event.getAcquirer(),
                    event.getOfferPrice(),
                    event.getCashOrStock(),
                    event.getPremiumPercent(),
                    event.getArticle().getHeadline(),
                    event.getArticle().getSource().getName(),
                    event.getArticle().getSource().getType(),
                    event.getArticle().getUrl(),
                    event.getArticle().getPublishedAt(),
                    event.getDetectedAt(),
                    event.getMatchedPositiveKeywords(),
                    event.getMatchedNegativeKeywords(),
                    event.getFalsePositiveReasons(),
                    event.getExplanation(),
                    event.getReviewNotes()
            );
        }
    }

    public record EventReviewRequest(
            String targetTicker,
            ValidationStatus validationStatus,
            String reviewNotes
    ) {
    }
}
