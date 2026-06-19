package com.parsernews.service;

import com.parsernews.model.DealConfidence;
import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealStatus;
import com.parsernews.model.DealTiming;
import com.parsernews.model.PaymentType;
import com.parsernews.model.ReviewVerdict;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DealStageDetectionServiceTest {
    private final DealStageDetectionService service = new DealStageDetectionService();

    @Test
    void detectsOfferIncreaseAsEarlyOrMidStage() {
        NewsArticleEntity article = article(
                "Diana Shipping Inc. Raises Offer to Acquire Genco Shipping & Trading",
                "Diana raises offer to acquire Genco to $27.34 per share."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.PROPOSAL),
                likelyDeal(),
                relevance(DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH)
        );

        assertThat(insight.dealStage()).isEqualTo(DealStage.OFFER_INCREASE);
        assertThat(insight.dealTiming()).isIn(DealTiming.EARLY, DealTiming.MID_STAGE);
        assertThat(insight.stagePositiveSignals()).contains("offer increase");
    }

    @Test
    void detectsDefinitiveAgreementAsEarlyAnnouncement() {
        NewsArticleEntity article = article(
                "Company X Enters into Definitive Agreement to Acquire Company Y",
                "Company X enters into definitive agreement to acquire Company Y for $10.00 per share in cash."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.DEFINITIVE_AGREEMENT),
                likelyDeal(),
                relevance(DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH)
        );

        assertThat(insight.dealStage()).isIn(DealStage.INITIAL_ANNOUNCEMENT, DealStage.DEFINITIVE_AGREEMENT);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.EARLY);
    }

    @Test
    void detectsShareholderApprovalAsLateStage() {
        NewsArticleEntity article = article(
                "Independent Bank Corporation Announces Shareholder Approval to Acquire HCB Financial Corp",
                "Shareholders approved the acquisition at a special meeting."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.MERGER_AGREEMENT),
                likelyDeal(),
                relevance(DealRelevance.PUBLIC_PUBLIC_MERGER, Tradability.MEDIUM)
        );

        assertThat(insight.dealStage()).isEqualTo(DealStage.SHAREHOLDER_APPROVAL);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.LATE_STAGE);
        assertThat(insight.stageWarnings()).contains("not initial announcement");
    }

    @Test
    void detectsRegulatoryApprovalAsLateStage() {
        NewsArticleEntity article = article(
                "Company Receives Regulatory Approvals for Merger",
                "The parties received regulatory approvals needed to complete the merger."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.MERGER_AGREEMENT),
                likelyDeal(),
                relevance(DealRelevance.PUBLIC_PUBLIC_MERGER, Tradability.MEDIUM)
        );

        assertThat(insight.dealStage()).isEqualTo(DealStage.REGULATORY_APPROVAL);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.LATE_STAGE);
    }

    @Test
    void subjectToRegulatoryApprovalDoesNotBecomeLateStageApproval() {
        NewsArticleEntity article = article(
                "MDA Space announces definitive agreement to acquire US-based Blue Canyon Technologies LLC",
                "The transaction is subject to regulatory approvals and customary closing conditions. "
                        + "It is expected to close in Q4."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.DEFINITIVE_AGREEMENT),
                likelyDeal(),
                relevance(DealRelevance.PRIVATE_COMPANY_ACQUISITION, Tradability.NOT_TRADABLE)
        );

        assertThat(insight.dealStage()).isIn(DealStage.INITIAL_ANNOUNCEMENT, DealStage.DEFINITIVE_AGREEMENT);
        assertThat(insight.dealStage()).isNotEqualTo(DealStage.REGULATORY_APPROVAL);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.EARLY);
        assertThat(insight.dealTiming()).isNotEqualTo(DealTiming.LATE_STAGE);
    }

    @Test
    void definitiveAgreementWithExpectedCloseBoilerplateIsNotClosingExpected() {
        NewsArticleEntity article = article(
                "Company X announces definitive agreement to acquire Company Y",
                "The transaction is expected to close in Q4 subject to customary closing conditions."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.DEFINITIVE_AGREEMENT),
                likelyDeal(),
                relevance(DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH)
        );

        assertThat(insight.dealStage()).isIn(DealStage.INITIAL_ANNOUNCEMENT, DealStage.DEFINITIVE_AGREEMENT);
        assertThat(insight.dealStage()).isNotEqualTo(DealStage.CLOSING_EXPECTED);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.EARLY);
    }

    @Test
    void expectedClosingDateHeadlineIsLateStageClosingUpdate() {
        NewsArticleEntity article = article(
                "Company X announces expected closing date for merger",
                "The merger is expected to close next week."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.UNKNOWN),
                likelyDeal(),
                relevance(DealRelevance.PUBLIC_PUBLIC_MERGER, Tradability.MEDIUM)
        );

        assertThat(insight.dealStage()).isEqualTo(DealStage.CLOSING_EXPECTED);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.LATE_STAGE);
    }

    @Test
    void detectsCompletedAcquisitionAsPostClose() {
        NewsArticleEntity article = article(
                "Company X Completes Acquisition of Company Y",
                "Company X completed acquisition of Company Y and closed the transaction."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.DEFINITIVE_AGREEMENT),
                likelyDeal(),
                relevance(DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH)
        );

        assertThat(insight.dealStage()).isEqualTo(DealStage.COMPLETION_OR_CLOSED);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.POST_CLOSE);
    }

    @Test
    void detectsShareholderLawFirmAlertAsNoise() {
        NewsArticleEntity article = article(
                "Shareholder Alert: Law Firm Investigates Proposed Merger",
                "A law firm investigates a proposed merger and possible class action."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.UNKNOWN),
                lawFirmAlert(),
                relevance(DealRelevance.LAW_FIRM_OR_SHAREHOLDER_ALERT, Tradability.NOT_TRADABLE)
        );

        assertThat(insight.dealStage()).isEqualTo(DealStage.LITIGATION_OR_LAW_FIRM_UPDATE);
        assertThat(insight.dealTiming()).isEqualTo(DealTiming.NOISE);
    }

    @Test
    void proposedReverseTakeoverSubjectToApprovalIsNotLateStageShareholderApproval() {
        NewsArticleEntity article = article(
                "Goldflare Announces Proposed Reverse Takeover with Quitovac Gold Holdings, LLC",
                "Goldflare entered into a non-binding letter of intent for a reverse takeover. "
                        + "Trading has been halted. The transaction is subject to shareholder approval "
                        + "and a definitive agreement is expected later."
        );

        DealStageDetectionService.StageInsight insight = service.detect(
                article,
                event(article),
                terms(DealStatus.PROPOSAL),
                likelyDeal(),
                relevance(DealRelevance.REVERSE_TAKEOVER, Tradability.NOT_TRADABLE)
        );

        assertThat(insight.dealStage()).isIn(DealStage.INITIAL_ANNOUNCEMENT, DealStage.RUMOR_OR_EXPLORATION);
        assertThat(insight.dealStage()).isNotEqualTo(DealStage.SHAREHOLDER_APPROVAL);
        assertThat(insight.dealTiming()).isIn(DealTiming.EARLY, DealTiming.MID_STAGE);
        assertThat(insight.dealTiming()).isNotEqualTo(DealTiming.LATE_STAGE);
        assertThat(insight.stageWarnings()).contains(
                "reverse takeover / RTO",
                "non-binding LOI",
                "definitive agreement not signed",
                "trading halted",
                "shareholder/regulatory approvals required"
        );
    }

    private NewsArticleEntity article(String headline, String body) {
        return new NewsArticleEntity(
                new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed"),
                "hash-" + headline.hashCode(),
                "UNKNOWN",
                "Test Company",
                headline,
                body,
                "https://example.com/article",
                Instant.parse("2026-06-18T10:00:00Z")
        );
    }

    private DetectedEventEntity event(NewsArticleEntity article) {
        return new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                90,
                null,
                "TEST",
                null,
                null,
                null,
                null,
                90,
                CandidateStrength.HIGH,
                "Matched candidate signal.",
                true,
                "Review test",
                "definitive agreement",
                "",
                "",
                "Test explanation"
        );
    }

    private DealTermsExtractionService.DealTerms terms(DealStatus dealStatus) {
        return new DealTermsExtractionService.DealTerms(
                "Company Y",
                "Company X",
                new BigDecimal("10.00"),
                "USD",
                PaymentType.CASH,
                dealStatus,
                DealConfidence.HIGH,
                List.of(),
                "Test terms"
        );
    }

    private CandidateReviewInsightService.ReviewInsight likelyDeal() {
        return new CandidateReviewInsightService.ReviewInsight(
                ReviewVerdict.LIKELY_DEAL,
                "Strong deal language.",
                List.of(),
                List.of("definitive agreement"),
                "Review source."
        );
    }

    private CandidateReviewInsightService.ReviewInsight lawFirmAlert() {
        return new CandidateReviewInsightService.ReviewInsight(
                ReviewVerdict.LAW_FIRM_ALERT,
                "Law firm alert.",
                List.of("law firm investigation"),
                List.of("merger agreement"),
                "Usually ignore."
        );
    }

    private DealRelevanceService.RelevanceInsight relevance(DealRelevance relevance, Tradability tradability) {
        return new DealRelevanceService.RelevanceInsight(
                relevance,
                tradability,
                "Test relevance",
                List.of(),
                List.of()
        );
    }
}
