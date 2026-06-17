package com.parsernews.web;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.service.AlertEligibilityService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
public class AlertCandidateController {
    private final DetectedEventRepository eventRepository;
    private final AlertEligibilityService alertEligibilityService;

    public AlertCandidateController(
            DetectedEventRepository eventRepository,
            AlertEligibilityService alertEligibilityService
    ) {
        this.eventRepository = eventRepository;
        this.alertEligibilityService = alertEligibilityService;
    }

    @GetMapping("/api/alerts/candidates")
    @Transactional(readOnly = true)
    public List<AlertCandidateResponse> candidates() {
        return eventRepository.findTop200ByOrderByDetectedAtDesc().stream()
                .filter(event -> event.isAlertEligible() && event.getAlertQueuedAt() == null)
                .sorted(Comparator.comparingInt(DetectedEventEntity::getCandidateScore).reversed()
                        .thenComparing(DetectedEventEntity::getDetectedAt, Comparator.reverseOrder()))
                .map(AlertCandidateResponse::from)
                .toList();
    }

    @PostMapping("/api/alerts/candidates/{id}/queue")
    @Transactional
    public AlertCandidateResponse queue(@PathVariable Long id) {
        DetectedEventEntity event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert candidate not found"));
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);
        if (!event.isAlertEligible() || !eligibility.eligible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, eligibility.reason());
        }
        event.markAlertQueued();
        return AlertCandidateResponse.from(event);
    }

    public record AlertCandidateResponse(
            Long detectedEventId,
            Long articleId,
            String title,
            String url,
            String source,
            String host,
            int candidateScore,
            CandidateStrength candidateStrength,
            String candidateReason,
            boolean alertEligible,
            Instant alertQueuedAt,
            String alertReason
    ) {
        static AlertCandidateResponse from(DetectedEventEntity event) {
            return new AlertCandidateResponse(
                    event.getId(),
                    event.getArticle().getId(),
                    event.getArticle().getHeadline(),
                    event.getArticle().getUrl(),
                    event.getArticle().getSource().getName(),
                    extractHost(event.getArticle().getUrl()),
                    event.getCandidateScore(),
                    event.getCandidateStrength(),
                    event.getCandidateReason(),
                    event.isAlertEligible(),
                    event.getAlertQueuedAt(),
                    event.getAlertReason()
            );
        }
    }

    private static String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
