package com.parsernews.service;

import com.parsernews.config.AlertDispatchSettings;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertDispatchServiceTest {
    @Test
    void dispatchDisabledReturnsDisabledResponseAndDoesNotMarkQueued() {
        DetectedEventEntity eligible = event(1L, CandidateStrength.HIGH, 90, true);
        AlertDispatchService service = service(false, 5, new NoOpAlertNotifier(), List.of(eligible));

        AlertDispatchService.AlertDispatchSummary summary = service.dispatch();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.disabledReason()).contains("disabled");
        assertThat(summary.attemptedCount()).isZero();
        assertThat(eligible.getAlertQueuedAt()).isNull();
    }

    @Test
    void dispatchEnabledWithNoOpNotifierDoesNotMarkQueued() {
        DetectedEventEntity eligible = event(2L, CandidateStrength.HIGH, 90, true);
        AlertDispatchService service = service(true, 5, new NoOpAlertNotifier(), List.of(eligible));

        AlertDispatchService.AlertDispatchSummary summary = service.dispatch();

        assertThat(summary.enabled()).isTrue();
        assertThat(summary.attemptedCount()).isEqualTo(1);
        assertThat(summary.sentCount()).isZero();
        assertThat(summary.failedCount()).isEqualTo(1);
        assertThat(summary.results().getFirst().status()).isEqualTo("DISABLED");
        assertThat(eligible.getAlertQueuedAt()).isNull();
        assertThat(eligible.isAlertEligible()).isTrue();
    }

    @Test
    void dispatchEnabledWithSuccessfulNotifierMarksQueued() {
        DetectedEventEntity eligible = event(3L, CandidateStrength.HIGH, 90, true);
        AlertNotifier notifier = message -> AlertNotifier.AlertNotificationResult.sent(
                "SENT",
                "Telegram alert message was sent."
        );
        AlertDispatchService service = service(true, 5, notifier, List.of(eligible));

        AlertDispatchService.AlertDispatchSummary summary = service.dispatch();

        assertThat(summary.sentCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isZero();
        assertThat(eligible.getAlertQueuedAt()).isNotNull();
        assertThat(eligible.isAlertEligible()).isFalse();
    }

    @Test
    void dispatchRespectsBatchSize() {
        DetectedEventEntity first = event(4L, CandidateStrength.HIGH, 100, true);
        DetectedEventEntity second = event(5L, CandidateStrength.HIGH, 90, true);
        AlertNotifier notifier = message -> AlertNotifier.AlertNotificationResult.sent("SENT", "Sent.");
        AlertDispatchService service = service(true, 1, notifier, List.of(first, second));

        AlertDispatchService.AlertDispatchSummary summary = service.dispatch();

        assertThat(summary.attemptedCount()).isEqualTo(1);
        assertThat(summary.sentCount()).isEqualTo(1);
        assertThat(first.getAlertQueuedAt()).isNotNull();
        assertThat(second.getAlertQueuedAt()).isNull();
    }

    @Test
    void dispatchSkipsAlreadyQueuedCandidate() {
        DetectedEventEntity queued = event(6L, CandidateStrength.HIGH, 90, true);
        queued.markAlertQueued();
        Instant queuedAt = queued.getAlertQueuedAt();
        AlertDispatchService service = service(true, 5, new NoOpAlertNotifier(), List.of(queued));

        AlertDispatchService.AlertDispatchSummary summary = service.dispatch();

        assertThat(summary.attemptedCount()).isZero();
        assertThat(summary.skippedCount()).isEqualTo(1);
        assertThat(summary.results().getFirst().status()).isEqualTo("ALREADY_QUEUED");
        assertThat(queued.getAlertQueuedAt()).isEqualTo(queuedAt);
    }

    private AlertDispatchService service(
            boolean enabled,
            int batchSize,
            AlertNotifier alertNotifier,
            List<DetectedEventEntity> events
    ) {
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findTop200ByOrderByDetectedAtDesc()).thenReturn(events);
        return new AlertDispatchService(
                repository,
                new AlertDispatchSettings(enabled, 300000, batchSize),
                new AlertEligibilityService(),
                new AlertMessageFormatter(),
                alertNotifier
        );
    }

    private DetectedEventEntity event(Long id, CandidateStrength strength, int score, boolean alertEligible) {
        NewsSourceEntity source = new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed");
        setId(source, id + 100);
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "hash-" + id,
                "TEST",
                "Test Company",
                "Test Company enters merger agreement",
                "Shareholders will receive $5.00 per share in cash.",
                "https://example.com/news/" + id,
                Instant.parse("2026-06-17T08:00:00Z")
        );
        setId(article, id + 200);
        DetectedEventEntity event = new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                80,
                "Test Company",
                "TEST",
                "Buyer LLC",
                "$5.00",
                "CASH",
                "40%",
                score,
                strength,
                "Matched " + strength + " candidate signal.",
                alertEligible,
                alertEligible ? "HIGH candidate from trusted source with positive score." : "Candidate strength is not HIGH.",
                "definitive agreement|per share in cash",
                "",
                "",
                "Matched test candidate"
        );
        setId(event, id);
        return event;
    }

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
