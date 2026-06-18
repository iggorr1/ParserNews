package com.parsernews.service;

import com.parsernews.model.ReviewVerdict;
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

class CandidateReviewInsightServiceTest {
    private final CandidateReviewInsightService service = new CandidateReviewInsightService();

    @Test
    void flagsDianaStyleHighCandidateWithOldIgnoredConflictAsNeedsReview() {
        NewsArticleEntity article = article(
                "Diana Shipping Inc. Raises Offer to Acquire Genco Shipping & Trading",
                "Diana entered into a definitive agreement. Shareholders will receive $24.80 per share in cash."
        );
        DetectedEventEntity event = event(article, CandidateStrength.HIGH, 90, ReviewStatus.IGNORED,
                "definitive agreement|per share in cash|shareholders will receive|to acquire",
                "");

        CandidateReviewInsightService.ReviewInsight insight = service.insight(article, event);

        assertThat(insight.reviewVerdict()).isEqualTo(ReviewVerdict.NEEDS_REVIEW);
        assertThat(insight.reviewPositiveSignals()).contains("definitive agreement", "per share in cash");
        assertThat(insight.reviewRiskFlags()).contains("old reviewStatus IGNORED but candidateStrength HIGH");
    }

    @Test
    void flagsShareholderAlertLawFirmAsLawFirmAlert() {
        NewsArticleEntity article = article(
                "Shareholder Alert: Law Firm Investigates Proposed Merger",
                "A law firm announces an investigation and potential class action."
        );
        DetectedEventEntity event = event(article, CandidateStrength.HIGH, 90, ReviewStatus.MANUAL_REVIEW,
                "merger agreement",
                "");

        CandidateReviewInsightService.ReviewInsight insight = service.insight(article, event);

        assertThat(insight.reviewVerdict()).isEqualTo(ReviewVerdict.LAW_FIRM_ALERT);
        assertThat(insight.reviewRiskFlags()).contains("shareholder alert", "law firm investigation", "class action");
    }

    @Test
    void flagsPublicOfferingAsFinancingOrOffering() {
        NewsArticleEntity article = article(
                "Company Announces Registered Direct Offering",
                "The public offering and private placement will fund operations."
        );
        DetectedEventEntity event = event(article, CandidateStrength.NONE, 0, ReviewStatus.IGNORED,
                "",
                "registered direct offering|public offering");

        CandidateReviewInsightService.ReviewInsight insight = service.insight(article, event);

        assertThat(insight.reviewVerdict()).isEqualTo(ReviewVerdict.FINANCING_OR_OFFERING);
        assertThat(insight.reviewRiskFlags()).contains("public offering", "registered direct offering", "private placement");
    }

    @Test
    void flagsWeakNegativeCandidateAsLikelyNoise() {
        NewsArticleEntity article = article(
                "Company Announces Joint Venture",
                "The transaction is a joint venture and asset acquisition."
        );
        DetectedEventEntity event = event(article, CandidateStrength.LOW, 10, ReviewStatus.IGNORED,
                "",
                "joint venture|asset acquisition");

        CandidateReviewInsightService.ReviewInsight insight = service.insight(article, event);

        assertThat(insight.reviewVerdict()).isEqualTo(ReviewVerdict.LIKELY_NOISE);
        assertThat(insight.reviewRiskFlags()).contains("joint venture", "asset acquisition");
    }

    private NewsArticleEntity article(String headline, String body) {
        return new NewsArticleEntity(
                new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed"),
                "hash-" + headline.hashCode(),
                "TEST",
                "Test Company",
                headline,
                body,
                "https://example.com/article",
                Instant.parse("2026-06-18T10:00:00Z")
        );
    }

    private DetectedEventEntity event(
            NewsArticleEntity article,
            CandidateStrength strength,
            int score,
            ReviewStatus reviewStatus,
            String positives,
            String negatives
    ) {
        return new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                reviewStatus,
                score,
                "Test Company",
                article.getTicker(),
                "Buyer LLC",
                "$5.00",
                "CASH",
                "40%",
                score,
                strength,
                "Matched candidate signal.",
                strength == CandidateStrength.HIGH,
                "Review test",
                positives,
                negatives,
                negatives,
                "Test explanation"
        );
    }
}
