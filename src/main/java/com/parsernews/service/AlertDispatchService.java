package com.parsernews.service;

import com.parsernews.config.AlertDispatchSettings;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AlertDispatchService {
    private final DetectedEventRepository eventRepository;
    private final AlertDispatchSettings settings;
    private final AlertEligibilityService alertEligibilityService;
    private final AlertMessageFormatter alertMessageFormatter;
    private final AlertNotifier alertNotifier;

    public AlertDispatchService(
            DetectedEventRepository eventRepository,
            AlertDispatchSettings settings,
            AlertEligibilityService alertEligibilityService,
            AlertMessageFormatter alertMessageFormatter,
            AlertNotifier alertNotifier
    ) {
        this.eventRepository = eventRepository;
        this.settings = settings;
        this.alertEligibilityService = alertEligibilityService;
        this.alertMessageFormatter = alertMessageFormatter;
        this.alertNotifier = alertNotifier;
    }

    @Transactional
    public AlertDispatchSummary dispatch() {
        if (!settings.enabled()) {
            return AlertDispatchSummary.disabled("Alert dispatch is disabled by configuration.");
        }
        DispatchCounter counter = new DispatchCounter(settings.enabled());
        eventRepository.findTop200ByOrderByDetectedAtDesc().stream()
                .sorted(Comparator.comparingInt(DetectedEventEntity::getCandidateScore).reversed()
                        .thenComparing(DetectedEventEntity::getDetectedAt, Comparator.reverseOrder()))
                .forEach(event -> processCandidate(event, counter));
        return counter.toSummary();
    }

    private void processCandidate(DetectedEventEntity event, DispatchCounter counter) {
        if (event.getAlertQueuedAt() != null) {
            counter.addSkipped(event.getId(), "ALREADY_QUEUED", "Candidate was already queued for alert.");
            return;
        }
        if (counter.attemptedCount >= settings.batchSize()) {
            return;
        }
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);
        if (!event.isAlertEligible() || !eligibility.eligible()) {
            counter.addSkipped(event.getId(), "NOT_ELIGIBLE", eligibility.reason());
            return;
        }
        counter.attemptedCount++;
        AlertNotifier.AlertNotificationResult notification = alertNotifier.send(alertMessageFormatter.format(event));
        if (notification.sent()) {
            event.markAlertQueued();
            counter.addSent(event.getId(), notification.status(), notification.reason());
        } else {
            counter.addFailed(event.getId(), notification.status(), notification.reason());
        }
    }

    public record AlertDispatchSummary(
            boolean enabled,
            int attemptedCount,
            int sentCount,
            int skippedCount,
            int failedCount,
            String disabledReason,
            List<AlertDispatchResult> results
    ) {
        static AlertDispatchSummary disabled(String disabledReason) {
            return new AlertDispatchSummary(false, 0, 0, 0, 0, disabledReason, List.of());
        }
    }

    public record AlertDispatchResult(
            Long detectedEventId,
            boolean sent,
            boolean skipped,
            String status,
            String reason
    ) {
    }

    private static class DispatchCounter {
        private final boolean enabled;
        private final java.util.ArrayList<AlertDispatchResult> results = new java.util.ArrayList<>();
        private int attemptedCount;
        private int sentCount;
        private int skippedCount;
        private int failedCount;

        private DispatchCounter(boolean enabled) {
            this.enabled = enabled;
        }

        private void addSent(Long id, String status, String reason) {
            sentCount++;
            results.add(new AlertDispatchResult(id, true, false, status, reason));
        }

        private void addSkipped(Long id, String status, String reason) {
            skippedCount++;
            results.add(new AlertDispatchResult(id, false, true, status, reason));
        }

        private void addFailed(Long id, String status, String reason) {
            failedCount++;
            results.add(new AlertDispatchResult(id, false, false, status, reason));
        }

        private AlertDispatchSummary toSummary() {
            return new AlertDispatchSummary(
                    enabled,
                    attemptedCount,
                    sentCount,
                    skippedCount,
                    failedCount,
                    null,
                    List.copyOf(results)
            );
        }
    }
}
