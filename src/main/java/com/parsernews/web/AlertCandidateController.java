package com.parsernews.web;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.service.AlertEligibilityService;
import com.parsernews.service.AlertMessageFormatter;
import com.parsernews.service.AlertNotifier;
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
    private final AlertMessageFormatter alertMessageFormatter;
    private final AlertNotifier alertNotifier;

    public AlertCandidateController(
            DetectedEventRepository eventRepository,
            AlertEligibilityService alertEligibilityService,
            AlertMessageFormatter alertMessageFormatter,
            AlertNotifier alertNotifier
    ) {
        this.eventRepository = eventRepository;
        this.alertEligibilityService = alertEligibilityService;
        this.alertMessageFormatter = alertMessageFormatter;
        this.alertNotifier = alertNotifier;
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

    @GetMapping("/api/alerts/candidates/{id}/preview")
    @Transactional(readOnly = true)
    public AlertPreviewResponse preview(@PathVariable Long id) {
        DetectedEventEntity event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert candidate not found"));
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);
        if (!event.isAlertEligible() || !eligibility.eligible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, eligibility.reason());
        }
        return AlertPreviewResponse.from(event, eligibility, alertMessageFormatter.format(event));
    }

    @PostMapping("/api/alerts/candidates/{id}/send")
    @Transactional
    public AlertSendResponse send(@PathVariable Long id) {
        DetectedEventEntity event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert candidate not found"));
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);
        if (!event.isAlertEligible() || !eligibility.eligible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, eligibility.reason());
        }
        String message = alertMessageFormatter.format(event);
        AlertNotifier.AlertNotificationResult notification = alertNotifier.send(message);
        if (notification.sent()) {
            event.markAlertQueued();
        }
        return AlertSendResponse.from(event, eligibility, notification, message);
    }

    @PostMapping("/api/alerts/dry-run")
    @Transactional(readOnly = true)
    public List<AlertPreviewResponse> dryRun() {
        return eventRepository.findTop200ByOrderByDetectedAtDesc().stream()
                .filter(event -> event.isAlertEligible() && event.getAlertQueuedAt() == null)
                .sorted(Comparator.comparingInt(DetectedEventEntity::getCandidateScore).reversed()
                        .thenComparing(DetectedEventEntity::getDetectedAt, Comparator.reverseOrder()))
                .map(event -> AlertPreviewResponse.from(
                        event,
                        alertEligibilityService.evaluate(event),
                        alertMessageFormatter.format(event)
                ))
                .toList();
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

    public record AlertPreviewResponse(
            Long detectedEventId,
            Long articleId,
            boolean eligible,
            boolean queued,
            String reason,
            String message
    ) {
        static AlertPreviewResponse from(
                DetectedEventEntity event,
                AlertEligibilityService.AlertEligibility eligibility,
                String message
        ) {
            return new AlertPreviewResponse(
                    event.getId(),
                    event.getArticle().getId(),
                    eligibility.eligible(),
                    event.getAlertQueuedAt() != null,
                    eligibility.reason(),
                    message
            );
        }
    }

    public record AlertSendResponse(
            Long detectedEventId,
            Long articleId,
            boolean eligible,
            boolean queued,
            boolean sent,
            String notifierStatus,
            String reason,
            String message
    ) {
        static AlertSendResponse from(
                DetectedEventEntity event,
                AlertEligibilityService.AlertEligibility eligibility,
                AlertNotifier.AlertNotificationResult notification,
                String message
        ) {
            return new AlertSendResponse(
                    event.getId(),
                    event.getArticle().getId(),
                    eligibility.eligible(),
                    event.getAlertQueuedAt() != null,
                    notification.sent(),
                    notification.status(),
                    notification.reason(),
                    message
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
