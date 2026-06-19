package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEligibilityServiceTest {
    private final AlertEligibilityService service = new AlertEligibilityService();

    @Test
    void highTrustedCandidateIsEligible() {
        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(
                CandidateStrength.HIGH,
                90,
                "https://www.globenewswire.com/news/test",
                false
        );

        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.reason()).contains("HIGH");
    }

    @Test
    void mediumLowAndNoneAreNotEligible() {
        assertThat(service.evaluate(CandidateStrength.MEDIUM, 60, "https://example.com/news", false).eligible()).isFalse();
        assertThat(service.evaluate(CandidateStrength.LOW, 30, "https://example.com/news", false).eligible()).isFalse();
        assertThat(service.evaluate(CandidateStrength.NONE, 0, "https://example.com/news", false).eligible()).isFalse();
    }

    @Test
    void alreadyQueuedCandidateIsNotEligible() {
        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(
                CandidateStrength.HIGH,
                90,
                "https://example.com/news",
                true
        );

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("already queued");
    }

    @Test
    void untrustedSourceIsNotEligible() {
        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(
                CandidateStrength.HIGH,
                90,
                "https://untrusted.test/news",
                false
        );

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("not trusted");
    }

    @Test
    void roundupEventIsNotAlertEligibleEvenIfStoredAsHigh() {
        NewsArticleEntity article = new NewsArticleEntity(
                new NewsSourceEntity("PR Newswire", NewsSourceType.RSS, "https://www.prnewswire.com/rss/news-releases-list.rss"),
                "hash-roundup",
                "UNKNOWN",
                "PR Newswire",
                "13 Press Releases You Need to See This Week",
                "Weekly roundup with definitive agreement headlines.",
                "https://www.prnewswire.com/news-releases/roundup",
                Instant.parse("2026-06-18T10:00:00Z")
        );
        DetectedEventEntity event = new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                90,
                null,
                "UNKNOWN",
                null,
                null,
                null,
                null,
                90,
                CandidateStrength.HIGH,
                "Matched HIGH candidate signal.",
                true,
                "Review test",
                "definitive agreement",
                "",
                "",
                "Test explanation"
        );

        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(event);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("Roundup/aggregator");
    }
}
