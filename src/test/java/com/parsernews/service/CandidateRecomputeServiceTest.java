package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.CompanyMatchConfidence;
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

class CandidateRecomputeServiceTest {
    @Test
    void recomputeUpdatesOldEventWithMissingScoreFields() {
        DetectedEventEntity event = event(
                "Old Company to be acquired by Buyer Inc.",
                "NASDAQ: TEST shareholders will receive $5.00 per share in cash in an all-cash transaction.",
                "https://example.com/news/old-company"
        );
        setField(event, "candidateScore", null);
        setField(event, "candidateStrength", null);
        setField(event, "candidateReason", null);
        setField(event, "alertEligible", null);
        setField(event, "alertReason", null);
        CandidateRecomputeService service = serviceFor(List.of(event));

        CandidateRecomputeService.RecomputeSummary summary = service.recomputeCandidates();

        assertThat(event.getCandidateScore()).isEqualTo(90);
        assertThat(event.getCandidateStrength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(event.getCandidateReason()).contains("HIGH");
        assertThat(event.isAlertEligible()).isTrue();
        assertThat(event.getAlertReason()).contains("Strategy-eligible");
        assertThat(event.getTargetCik()).isEqualTo("123456");
        assertThat(event.getTargetMatchConfidence()).isEqualTo(CompanyMatchConfidence.EXACT_TICKER);
        assertThat(summary.scannedEvents()).isEqualTo(1);
        assertThat(summary.updatedEvents()).isEqualTo(1);
        assertThat(summary.highCount()).isEqualTo(1);
        assertThat(summary.alertEligibleCount()).isEqualTo(1);
    }

    @Test
    void recomputeDoesNotResetAlertQueuedAt() {
        DetectedEventEntity event = event(
                "Queued Company enters definitive agreement",
                "Queued Company entered into a merger agreement with Buyer LLC.",
                "https://example.com/news/queued-company"
        );
        event.markAlertQueued();
        Instant queuedAt = event.getAlertQueuedAt();
        CandidateRecomputeService service = serviceFor(List.of(event));

        service.recomputeCandidates();

        assertThat(event.getAlertQueuedAt()).isEqualTo(queuedAt);
    }

    @Test
    void recomputeDoesNotMakeAlreadyQueuedEventEligibleAgain() {
        DetectedEventEntity event = event(
                "Queued Company to be acquired by Buyer LLC",
                "Shareholders will receive cash consideration.",
                "https://example.com/news/already-queued"
        );
        event.markAlertQueued();
        CandidateRecomputeService service = serviceFor(List.of(event));

        CandidateRecomputeService.RecomputeSummary summary = service.recomputeCandidates();

        assertThat(event.getCandidateStrength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(event.isAlertEligible()).isFalse();
        assertThat(event.getAlertReason()).contains("already queued");
        assertThat(summary.alertEligibleCount()).isZero();
        assertThat(summary.alreadyQueuedCount()).isEqualTo(1);
    }

    private CandidateRecomputeService serviceFor(List<DetectedEventEntity> events) {
        DetectedEventRepository repository = mock(DetectedEventRepository.class);
        when(repository.findAll()).thenReturn(events);
        RssCompanyEnrichmentService enrichmentService = mock(RssCompanyEnrichmentService.class);
        when(enrichmentService.enrich(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RssCompanyEnrichmentService.CompanyEnrichment(
                        new RssCompanyEnrichmentService.CompanyRoleEnrichment(
                                "TEST",
                                "123456",
                                true,
                                CompanyMatchConfidence.EXACT_TICKER
                        ),
                        new RssCompanyEnrichmentService.CompanyRoleEnrichment(
                                null,
                                null,
                                false,
                                CompanyMatchConfidence.NONE
                        ),
                        List.of()
                ));
        return new CandidateRecomputeService(repository, new CandidateScoringService(), new AlertEligibilityService(), enrichmentService);
    }

    private DetectedEventEntity event(String headline, String body, String url) {
        NewsSourceEntity source = new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed");
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "hash-" + Math.abs(url.hashCode()),
                "TEST",
                "Test Company",
                headline,
                body,
                url,
                Instant.parse("2026-06-17T08:00:00Z")
        );
        return new DetectedEventEntity(
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
                0,
                CandidateStrength.NONE,
                null,
                false,
                null,
                "merger agreement",
                "",
                "",
                "Matched test candidate"
        );
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
