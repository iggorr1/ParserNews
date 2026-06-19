package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.ManualReviewStatus;
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

    @Test
    void mdaPrivateCompanyAcquisitionIsNotAlertEligibleDespiteHighScore() {
        DetectedEventEntity event = event(
                "MDA Space announces definitive agreement to acquire US-based Blue Canyon Technologies LLC",
                "MDA Space Ltd. (TSX:MDA) will acquire Blue Canyon Technologies LLC. "
                        + "The transaction is expected to close in Q4 subject to customary closing conditions, "
                        + "including regulatory approvals.",
                "MDA",
                "MDA Space",
                "Blue Canyon Technologies LLC",
                CandidateStrength.HIGH,
                90
        );

        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(event);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.dealRelevance()).isEqualTo(com.parsernews.model.DealRelevance.PRIVATE_COMPANY_ACQUISITION);
        assertThat(eligibility.tradability()).isIn(com.parsernews.model.Tradability.LOW, com.parsernews.model.Tradability.NOT_TRADABLE);
        assertThat(eligibility.dealTiming()).isEqualTo(com.parsernews.model.DealTiming.EARLY);
        assertThat(eligibility.dealStage()).isNotEqualTo(com.parsernews.model.DealStage.CLOSING_EXPECTED);
        assertThat(eligibility.reason()).contains("PRIVATE_COMPANY_ACQUISITION");
    }

    @Test
    void dianaGencoPublicCashOfferIncreaseRemainsAlertEligible() {
        DetectedEventEntity event = event(
                "Diana Shipping Inc. Raises Offer to Acquire Genco Shipping & Trading to $27.34 Per Share",
                "NASDAQ: GNK shareholders will receive $24.80 in cash and one Diana share. "
                        + "Diana raises offer to acquire Genco Shipping & Trading.",
                "GNK",
                "Diana Shipping Inc.",
                "Genco Shipping & Trading",
                CandidateStrength.HIGH,
                90
        );

        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(event);

        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.dealRelevance()).isEqualTo(com.parsernews.model.DealRelevance.PUBLIC_CASH_ACQUISITION);
        assertThat(eligibility.tradability()).isEqualTo(com.parsernews.model.Tradability.HIGH);
        assertThat(eligibility.dealTiming()).isEqualTo(com.parsernews.model.DealTiming.EARLY);
    }

    @Test
    void goldflareReverseTakeoverIsNotAlertEligible() {
        DetectedEventEntity event = event(
                "Goldflare Announces Proposed Reverse Takeover with Quitovac Gold Holdings, LLC",
                "Goldflare entered into a non-binding letter of intent for a reverse takeover. "
                        + "Trading has been halted and a definitive agreement is expected later.",
                "GOFL",
                "Goldflare",
                "Quitovac Gold Holdings, LLC",
                CandidateStrength.HIGH,
                90
        );

        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(event);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.dealRelevance()).isEqualTo(com.parsernews.model.DealRelevance.REVERSE_TAKEOVER);
        assertThat(eligibility.tradability()).isIn(com.parsernews.model.Tradability.LOW, com.parsernews.model.Tradability.NOT_TRADABLE);
        assertThat(eligibility.reason()).contains("REVERSE_TAKEOVER");
    }

    @Test
    void manuallyIgnoredCandidateIsNotAlertEligible() {
        DetectedEventEntity event = event(
                "TargetCo to be acquired by Sponsor Partners",
                "NASDAQ: TGT shareholders will receive $12.50 per share in cash.",
                "TGT",
                "Sponsor Partners",
                "TargetCo",
                CandidateStrength.HIGH,
                90
        );
        event.updateManualReview(ManualReviewStatus.IGNORED, "Not useful.");

        AlertEligibilityService.AlertEligibility eligibility = service.evaluate(event);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).contains("manually ignored");
    }

    private DetectedEventEntity event(
            String headline,
            String body,
            String ticker,
            String acquirer,
            String targetCompany,
            CandidateStrength strength,
            int score
    ) {
        NewsArticleEntity article = new NewsArticleEntity(
                new NewsSourceEntity("Example News", NewsSourceType.RSS, "https://example.com/feed"),
                "hash-" + headline.hashCode(),
                ticker,
                targetCompany,
                headline,
                body,
                "https://example.com/news/" + Math.abs(headline.hashCode()),
                Instant.parse("2026-06-18T10:00:00Z")
        );
        return new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                score,
                targetCompany,
                ticker,
                acquirer,
                null,
                null,
                null,
                score,
                strength,
                "Matched HIGH candidate signal.",
                true,
                "Review test",
                "definitive agreement|per share in cash|to acquire",
                "",
                "",
                "Test explanation"
        );
    }
}
