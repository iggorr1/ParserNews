package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.CompanyMatchConfidence;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RssCompanyEnrichmentServiceTest {
    private SecCompanyLookupService lookupService;
    private CandidateReviewInsightService reviewInsightService;
    private DealTermsExtractionService dealTermsExtractionService;

    @BeforeEach
    void setUp() {
        lookupService = mock(SecCompanyLookupService.class);
        reviewInsightService = mock(CandidateReviewInsightService.class);
        dealTermsExtractionService = mock(DealTermsExtractionService.class);
        when(reviewInsightService.insight(any(), any())).thenReturn(new CandidateReviewInsightService.ReviewInsight(
                com.parsernews.model.ReviewVerdict.LIKELY_DEAL,
                "Likely deal.",
                List.of(),
                List.of("merger agreement"),
                "Review"
        ));
    }

    @Test
    void exactTickerInTitleResolvesTargetTickerAndCik() {
        NewsArticleEntity article = article(
                "Genco Shipping & Trading (GNK) to be acquired by Buyer",
                "Shareholders will receive cash."
        );
        DetectedEventEntity event = event(article, "UNKNOWN");
        mockTerms("Genco Shipping & Trading", "Buyer");
        when(lookupService.findBestMatch(eq("GNK"), eq("Genco Shipping & Trading")))
                .thenReturn(Optional.of(new SecCompanyLookupService.CompanyLookupMatch(
                        "1326200",
                        "GNK",
                        "Genco Shipping & Trading Ltd.",
                        CompanyMatchConfidence.EXACT_TICKER
                )));

        RssCompanyEnrichmentService.CompanyEnrichment enrichment = service().enrich(article, event);

        assertThat(enrichment.target().ticker()).isEqualTo("GNK");
        assertThat(enrichment.target().cik()).isEqualTo("1326200");
        assertThat(enrichment.target().publicCompany()).isTrue();
        assertThat(enrichment.target().matchConfidence()).isEqualTo(CompanyMatchConfidence.EXACT_TICKER);
    }

    @Test
    void exactCompanyNameResolvesTargetWithConfidence() {
        NewsArticleEntity article = article(
                "Diana Shipping Raises Offer to Acquire Genco Shipping & Trading",
                "Diana Shipping raises offer to acquire Genco Shipping & Trading."
        );
        DetectedEventEntity event = event(article, "UNKNOWN");
        mockTerms("Genco Shipping & Trading", "Diana Shipping");
        when(lookupService.findBestMatch(eq(null), eq("Genco Shipping & Trading")))
                .thenReturn(Optional.of(new SecCompanyLookupService.CompanyLookupMatch(
                        "1326200",
                        "GNK",
                        "Genco Shipping & Trading Ltd.",
                        CompanyMatchConfidence.EXACT_NAME
                )));

        RssCompanyEnrichmentService.CompanyEnrichment enrichment = service().enrich(article, event);

        assertThat(enrichment.target().ticker()).isEqualTo("GNK");
        assertThat(enrichment.target().publicCompany()).isTrue();
        assertThat(enrichment.target().matchConfidence()).isEqualTo(CompanyMatchConfidence.EXACT_NAME);
    }

    @Test
    void publicBuyerPrivateTargetDoesNotCreatePublicTarget() {
        NewsArticleEntity article = article(
                "MDA Space announces definitive agreement to acquire Blue Canyon Technologies LLC",
                "MDA Space (MDA) will acquire Blue Canyon Technologies LLC."
        );
        DetectedEventEntity event = event(article, "MDA");
        mockTerms("Blue Canyon Technologies LLC", "MDA Space");
        when(lookupService.findBestMatch(eq(null), eq("Blue Canyon Technologies LLC")))
                .thenReturn(Optional.empty());
        when(lookupService.findBestMatch(eq("MDA"), eq("MDA Space")))
                .thenReturn(Optional.of(new SecCompanyLookupService.CompanyLookupMatch(
                        "1234567",
                        "MDA",
                        "MDA Space Ltd.",
                        CompanyMatchConfidence.EXACT_NAME
                )));

        RssCompanyEnrichmentService.CompanyEnrichment enrichment = service().enrich(article, event);

        assertThat(enrichment.target().publicCompany()).isFalse();
        assertThat(enrichment.target().ticker()).isNull();
        assertThat(enrichment.buyer().publicCompany()).isTrue();
        assertThat(enrichment.warnings()).contains("buyer resolved but target not resolved", "private target signal");
    }

    @Test
    void genericPartialNameDoesNotCreateStrongPublicTarget() {
        NewsArticleEntity article = article(
                "Company Announces Acquisition of Business Unit",
                "The Company acquired a business unit."
        );
        DetectedEventEntity event = event(article, "UNKNOWN");
        mockTerms("Business Unit", "The Company");

        RssCompanyEnrichmentService.CompanyEnrichment enrichment = service().enrich(article, event);

        assertThat(enrichment.target().matchConfidence()).isEqualTo(CompanyMatchConfidence.NONE);
        assertThat(enrichment.target().publicCompany()).isFalse();
    }

    private RssCompanyEnrichmentService service() {
        return new RssCompanyEnrichmentService(lookupService, reviewInsightService, dealTermsExtractionService);
    }

    private void mockTerms(String target, String buyer) {
        when(dealTermsExtractionService.extract(any(), any(), any())).thenReturn(new DealTermsExtractionService.DealTerms(
                target,
                buyer,
                null,
                null,
                com.parsernews.model.PaymentType.CASH,
                com.parsernews.model.DealStatus.DEFINITIVE_AGREEMENT,
                com.parsernews.model.DealConfidence.HIGH,
                List.of(),
                "Test terms"
        ));
    }

    private NewsArticleEntity article(String headline, String body) {
        return new NewsArticleEntity(
                new NewsSourceEntity("Test Source", NewsSourceType.RSS, "https://example.com/feed"),
                "hash-" + Math.abs(headline.hashCode()),
                "UNKNOWN",
                "Test Company",
                headline,
                body,
                "https://example.com/article",
                Instant.parse("2026-06-18T10:00:00Z")
        );
    }

    private DetectedEventEntity event(NewsArticleEntity article, String ticker) {
        return new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                90,
                "Test Company",
                ticker,
                null,
                null,
                null,
                null,
                90,
                CandidateStrength.HIGH,
                "Matched candidate signal.",
                false,
                null,
                "definitive agreement",
                "",
                "",
                "Test explanation"
        );
    }
}
