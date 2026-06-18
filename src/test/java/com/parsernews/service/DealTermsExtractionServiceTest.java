package com.parsernews.service;

import com.parsernews.model.DealConfidence;
import com.parsernews.model.DealStatus;
import com.parsernews.model.PaymentType;
import com.parsernews.model.ReviewVerdict;
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

class DealTermsExtractionServiceTest {
    private final DealTermsExtractionService service = new DealTermsExtractionService();

    @Test
    void extractsDianaGencoCashAndStockProposal() {
        NewsArticleEntity article = article(
                "Diana Shipping Inc. Raises Offer to Acquire Genco Shipping & Trading to $27.34 Per Share Comprised of $24.80 in Cash and One Diana Share",
                "Diana Shipping Inc. raises its offer to acquire Genco Shipping & Trading. Consideration is comprised of $24.80 in cash and one Diana share."
        );
        DetectedEventEntity event = event(article, CandidateStrength.HIGH, ReviewStatus.MANUAL_REVIEW);

        DealTermsExtractionService.DealTerms terms = service.extract(article, event, likelyDeal());

        assertThat(terms.buyerCompany()).isEqualTo("Diana Shipping Inc.");
        assertThat(terms.targetCompany()).isEqualTo("Genco Shipping & Trading");
        assertThat(terms.offerPrice()).isEqualByComparingTo(new BigDecimal("27.34"));
        assertThat(terms.offerCurrency()).isEqualTo("USD");
        assertThat(terms.paymentType()).isEqualTo(PaymentType.CASH_AND_STOCK);
        assertThat(terms.dealStatus()).isIn(DealStatus.PROPOSAL, DealStatus.DEFINITIVE_AGREEMENT);
    }

    @Test
    void extractsDefinitiveAgreementToAcquire() {
        NewsArticleEntity article = article(
                "Company X enters into definitive agreement to acquire Company Y",
                "The parties announced a definitive agreement."
        );
        DetectedEventEntity event = event(article, CandidateStrength.HIGH, ReviewStatus.HIGH_PRIORITY_SIGNAL);

        DealTermsExtractionService.DealTerms terms = service.extract(article, event, likelyDeal());

        assertThat(terms.buyerCompany()).isEqualTo("Company X");
        assertThat(terms.targetCompany()).isEqualTo("Company Y");
        assertThat(terms.dealStatus()).isEqualTo(DealStatus.DEFINITIVE_AGREEMENT);
    }

    @Test
    void extractsAcquiredByAllCashTransaction() {
        NewsArticleEntity article = article(
                "Company Y to be acquired by Company X in all-cash transaction",
                "Shareholders will receive $12.50 per share in cash."
        );
        DetectedEventEntity event = event(article, CandidateStrength.HIGH, ReviewStatus.HIGH_PRIORITY_SIGNAL);

        DealTermsExtractionService.DealTerms terms = service.extract(article, event, likelyDeal());

        assertThat(terms.targetCompany()).isEqualTo("Company Y");
        assertThat(terms.buyerCompany()).isEqualTo("Company X");
        assertThat(terms.paymentType()).isEqualTo(PaymentType.CASH);
        assertThat(terms.offerPrice()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void lawFirmAlertDoesNotGetHighConfidence() {
        NewsArticleEntity article = article(
                "Shareholder Alert: Law Firm Investigates Proposed Merger",
                "The law firm investigates whether shareholders are receiving fair value."
        );
        DetectedEventEntity event = event(article, CandidateStrength.HIGH, ReviewStatus.MANUAL_REVIEW);

        DealTermsExtractionService.DealTerms terms = service.extract(article, event, lawFirmAlert());

        assertThat(terms.dealConfidence()).isEqualTo(DealConfidence.LOW);
        assertThat(terms.dealWarnings()).contains("law firm alert");
    }

    @Test
    void missingPriceCreatesWarning() {
        NewsArticleEntity article = article(
                "Company X enters into definitive agreement to acquire Company Y",
                "The definitive agreement is expected to close this year."
        );
        DetectedEventEntity event = event(article, CandidateStrength.HIGH, ReviewStatus.HIGH_PRIORITY_SIGNAL);

        DealTermsExtractionService.DealTerms terms = service.extract(article, event, likelyDeal());

        assertThat(terms.dealWarnings()).contains("offer price missing");
    }

    private CandidateReviewInsightService.ReviewInsight likelyDeal() {
        return new CandidateReviewInsightService.ReviewInsight(
                ReviewVerdict.LIKELY_DEAL,
                "Strong deal language found.",
                List.of(),
                List.of("definitive agreement", "to be acquired by"),
                "Review source article."
        );
    }

    private CandidateReviewInsightService.ReviewInsight lawFirmAlert() {
        return new CandidateReviewInsightService.ReviewInsight(
                ReviewVerdict.LAW_FIRM_ALERT,
                "Looks like a law firm alert.",
                List.of("law firm investigation"),
                List.of("merger agreement"),
                "Usually ignore."
        );
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

    private DetectedEventEntity event(NewsArticleEntity article, CandidateStrength strength, ReviewStatus reviewStatus) {
        return new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                reviewStatus,
                90,
                null,
                article.getTicker(),
                null,
                null,
                null,
                null,
                90,
                strength,
                "Matched HIGH candidate signal: definitive agreement.",
                strength == CandidateStrength.HIGH,
                "Review test",
                "definitive agreement|to acquire|per share in cash",
                "",
                "",
                "Test explanation"
        );
    }
}
