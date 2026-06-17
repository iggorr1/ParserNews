package com.parsernews.web;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.service.AlertEligibilityService;
import com.parsernews.service.AlertMessageFormatter;
import com.parsernews.service.AlertNotifier;
import com.parsernews.service.NoOpAlertNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertCandidateControllerTest {
    @Test
    void returnsOnlyEligibleNonQueuedCandidates() {
        DetectedEventEntity eligible = event(1L, CandidateStrength.HIGH, 90, true);
        DetectedEventEntity low = event(2L, CandidateStrength.LOW, 30, false);
        DetectedEventEntity queued = event(3L, CandidateStrength.HIGH, 90, true);
        queued.markAlertQueued();
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(low, queued, eligible));
        AlertCandidateController controller = controller(repository);

        List<AlertCandidateController.AlertCandidateResponse> response = controller.candidates();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().detectedEventId()).isEqualTo(1L);
        assertThat(response.getFirst().alertEligible()).isTrue();
        assertThat(response.getFirst().candidateStrength()).isEqualTo(CandidateStrength.HIGH);
    }

    @Test
    void queueMarksEligibleCandidateAsQueued() {
        DetectedEventEntity eligible = event(10L, CandidateStrength.HIGH, 90, true);
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(10L)).thenReturn(Optional.of(eligible));
        AlertCandidateController controller = controller(repository);

        AlertCandidateController.AlertCandidateResponse response = controller.queue(10L);

        assertThat(response.detectedEventId()).isEqualTo(10L);
        assertThat(response.alertQueuedAt()).isNotNull();
        assertThat(response.alertEligible()).isFalse();
    }

    @Test
    void queueReturnsNotFoundForMissingCandidate() {
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(404L)).thenReturn(Optional.empty());
        AlertCandidateController controller = controller(repository);

        assertThatThrownBy(() -> controller.queue(404L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void queueReturnsBadRequestForNonEligibleCandidate() {
        DetectedEventEntity medium = event(20L, CandidateStrength.MEDIUM, 60, false);
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(20L)).thenReturn(Optional.of(medium));
        AlertCandidateController controller = controller(repository);

        assertThatThrownBy(() -> controller.queue(20L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void previewReturnsFormattedMessageForEligibleCandidate() {
        DetectedEventEntity eligible = event(30L, CandidateStrength.HIGH, 90, true);
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(30L)).thenReturn(Optional.of(eligible));
        AlertCandidateController controller = controller(repository);

        AlertCandidateController.AlertPreviewResponse response = controller.preview(30L);

        assertThat(response.detectedEventId()).isEqualTo(30L);
        assertThat(response.articleId()).isEqualTo(230L);
        assertThat(response.eligible()).isTrue();
        assertThat(response.queued()).isFalse();
        assertThat(response.message()).contains("Test Company enters merger agreement");
        assertThat(response.message()).contains("Test Source");
        assertThat(response.message()).contains("example.com");
        assertThat(response.message()).contains("Strength: HIGH");
        assertThat(response.message()).contains("Score: 90");
        assertThat(response.message()).contains("Matched HIGH candidate signal.");
        assertThat(response.message()).contains("https://example.com/news/30");
    }

    @Test
    void previewReturnsNotFoundForMissingCandidate() {
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(405L)).thenReturn(Optional.empty());
        AlertCandidateController controller = controller(repository);

        assertThatThrownBy(() -> controller.preview(405L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void dryRunReturnsEligibleNonQueuedPreviews() {
        DetectedEventEntity eligible = event(40L, CandidateStrength.HIGH, 90, true);
        DetectedEventEntity low = event(41L, CandidateStrength.LOW, 30, false);
        DetectedEventEntity queued = event(42L, CandidateStrength.HIGH, 90, true);
        queued.markAlertQueued();
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(low, queued, eligible));
        AlertCandidateController controller = controller(repository);

        List<AlertCandidateController.AlertPreviewResponse> response = controller.dryRun();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().detectedEventId()).isEqualTo(40L);
        assertThat(response.getFirst().eligible()).isTrue();
        assertThat(response.getFirst().queued()).isFalse();
        assertThat(response.getFirst().message()).contains("Score: 90");
    }

    @Test
    void dryRunDoesNotMarkCandidateAsQueued() {
        DetectedEventEntity eligible = event(50L, CandidateStrength.HIGH, 90, true);
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(eligible));
        AlertCandidateController controller = controller(repository);

        controller.dryRun();

        assertThat(eligible.getAlertQueuedAt()).isNull();
        assertThat(eligible.isAlertEligible()).isTrue();
    }

    @Test
    void sendWithTelegramDisabledDoesNotMarkCandidateQueued() {
        DetectedEventEntity eligible = event(60L, CandidateStrength.HIGH, 90, true);
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(60L)).thenReturn(Optional.of(eligible));
        AlertCandidateController controller = controller(repository);

        AlertCandidateController.AlertSendResponse response = controller.send(60L);

        assertThat(response.detectedEventId()).isEqualTo(60L);
        assertThat(response.sent()).isFalse();
        assertThat(response.queued()).isFalse();
        assertThat(response.notifierStatus()).isEqualTo("DISABLED");
        assertThat(response.reason()).contains("disabled");
        assertThat(response.message()).contains("Test Company enters merger agreement");
        assertThat(eligible.getAlertQueuedAt()).isNull();
        assertThat(eligible.isAlertEligible()).isTrue();
    }

    @Test
    void sendReturnsNotFoundForMissingCandidate() {
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(406L)).thenReturn(Optional.empty());
        AlertCandidateController controller = controller(repository);

        assertThatThrownBy(() -> controller.send(406L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void sendReturnsBadRequestForNonEligibleCandidate() {
        DetectedEventEntity medium = event(70L, CandidateStrength.MEDIUM, 60, false);
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(70L)).thenReturn(Optional.of(medium));
        AlertCandidateController controller = controller(repository);

        assertThatThrownBy(() -> controller.send(70L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void sendReturnsBadRequestForAlreadyQueuedCandidate() {
        DetectedEventEntity queued = event(80L, CandidateStrength.HIGH, 90, true);
        queued.markAlertQueued();
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(80L)).thenReturn(Optional.of(queued));
        AlertNotifier notifier = mock(AlertNotifier.class);
        AlertCandidateController controller = controller(repository, notifier);

        assertThatThrownBy(() -> controller.send(80L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        verify(notifier, never()).send(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sendMarksCandidateQueuedOnlyWhenNotifierSends() {
        DetectedEventEntity eligible = event(90L, CandidateStrength.HIGH, 90, true);
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findById(90L)).thenReturn(Optional.of(eligible));
        AlertNotifier notifier = mock(AlertNotifier.class);
        when(notifier.send(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AlertNotifier.AlertNotificationResult.sent("SENT", "Telegram alert message was sent."));
        AlertCandidateController controller = controller(repository, notifier);

        AlertCandidateController.AlertSendResponse response = controller.send(90L);

        assertThat(response.sent()).isTrue();
        assertThat(response.queued()).isTrue();
        assertThat(eligible.getAlertQueuedAt()).isNotNull();
        assertThat(eligible.isAlertEligible()).isFalse();
    }

    private AlertCandidateController controller(DetectedEventRepository repository) {
        return controller(repository, new NoOpAlertNotifier());
    }

    private AlertCandidateController controller(DetectedEventRepository repository, AlertNotifier alertNotifier) {
        return new AlertCandidateController(
                repository,
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
