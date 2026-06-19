package com.parsernews.service;

import com.parsernews.model.DealConfidence;
import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStatus;
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

class DealRelevanceServiceTest {
    private final DealRelevanceService service = new DealRelevanceService();

    @Test
    void classifiesPublicCashTakePrivateAsHighTradability() {
        NewsArticleEntity article = article(
                "TargetCo to be acquired by Sponsor Partners",
                "NASDAQ: TGT shareholders will receive $12.50 per share in cash. The company will become privately held."
        );
        DetectedEventEntity event = event(article, "TGT", CandidateStrength.HIGH, ReviewStatus.HIGH_PRIORITY_SIGNAL);

        DealRelevanceService.RelevanceInsight insight = service.assess(article, event, likelyDeal(), terms(
                "TargetCo",
                "Sponsor Partners",
                new BigDecimal("12.50"),
                PaymentType.CASH,
                DealStatus.DEFINITIVE_AGREEMENT
        ));

        assertThat(insight.dealRelevance()).isEqualTo(DealRelevance.PUBLIC_TAKE_PRIVATE);
        assertThat(insight.tradability()).isEqualTo(Tradability.HIGH);
        assertThat(insight.relevancePositiveSignals()).contains("take-private cash/per-share signal");
    }

    @Test
    void classifiesChicagoStylePublicPublicStockMergerAsLowerTradability() {
        NewsArticleEntity article = article(
                "Chicago Atlantic REFI and LIEN Announce Definitive Merger Agreement",
                "NASDAQ: REFI and NASDAQ: LIEN announce a stock-for-stock merger agreement for a combined company."
        );
        DetectedEventEntity event = event(article, "REFI", CandidateStrength.HIGH, ReviewStatus.HIGH_PRIORITY_SIGNAL);

        DealRelevanceService.RelevanceInsight insight = service.assess(article, event, likelyDeal(), terms(
                null,
                null,
                null,
                PaymentType.STOCK,
                DealStatus.MERGER_AGREEMENT
        ));

        assertThat(insight.dealRelevance()).isIn(DealRelevance.PUBLIC_PUBLIC_MERGER, DealRelevance.PUBLIC_STOCK_MERGER);
        assertThat(insight.tradability()).isIn(Tradability.MEDIUM, Tradability.LOW);
        assertThat(insight.relevanceWarnings()).contains("not take-private");
    }

    @Test
    void classifiesPrivateCompanyAcquisitionAsLowOrNotTradable() {
        NewsArticleEntity article = article(
                "Rise Baking Company Enters into Definitive Agreement to Acquire Jimmy's Gourmet Bakery",
                "Rise Baking Company will acquire Jimmy's Gourmet Bakery. Terms were not disclosed."
        );
        DetectedEventEntity event = event(article, "UNKNOWN", CandidateStrength.HIGH, ReviewStatus.MANUAL_REVIEW);

        DealRelevanceService.RelevanceInsight insight = service.assess(article, event, likelyDeal(), terms(
                "Jimmy's Gourmet Bakery",
                "Rise Baking Company",
                null,
                PaymentType.UNKNOWN,
                DealStatus.DEFINITIVE_AGREEMENT
        ));

        assertThat(insight.dealRelevance()).isIn(DealRelevance.PRIVATE_COMPANY_ACQUISITION, DealRelevance.NOT_TRADABLE);
        assertThat(insight.tradability()).isIn(Tradability.LOW, Tradability.NOT_TRADABLE);
        assertThat(insight.relevanceWarnings()).contains("private-company acquisition");
    }

    @Test
    void classifiesPublicBuyerAcquiringPrivateLlcTargetAsPrivateCompanyAcquisition() {
        NewsArticleEntity article = article(
                "MDA Space announces definitive agreement to acquire US-based Blue Canyon Technologies LLC",
                "MDA Space Ltd. (TSX:MDA) will acquire Blue Canyon Technologies LLC. "
                        + "The transaction is subject to regulatory approvals and customary closing conditions."
        );
        DetectedEventEntity event = event(article, "MDA", CandidateStrength.HIGH, ReviewStatus.HIGH_PRIORITY_SIGNAL);

        DealRelevanceService.RelevanceInsight insight = service.assess(article, event, likelyDeal(), terms(
                "Blue Canyon Technologies LLC",
                "MDA Space",
                null,
                PaymentType.UNKNOWN,
                DealStatus.DEFINITIVE_AGREEMENT
        ));

        assertThat(insight.dealRelevance()).isEqualTo(DealRelevance.PRIVATE_COMPANY_ACQUISITION);
        assertThat(insight.tradability()).isIn(Tradability.LOW, Tradability.NOT_TRADABLE);
        assertThat(insight.relevanceWarnings()).contains(
                "private company target",
                "no public target signal",
                "not directly tradable via target shares"
        );
    }

    @Test
    void classifiesLawFirmAlertAsNotTradable() {
        NewsArticleEntity article = article(
                "Shareholder Alert: Law Firm Investigates Proposed Merger",
                "A law firm investigates a proposed merger and possible class action."
        );
        DetectedEventEntity event = event(article, "TEST", CandidateStrength.HIGH, ReviewStatus.MANUAL_REVIEW);

        DealRelevanceService.RelevanceInsight insight = service.assess(article, event, lawFirmAlert(), terms(
                null,
                null,
                null,
                PaymentType.UNKNOWN,
                DealStatus.UNKNOWN
        ));

        assertThat(insight.dealRelevance()).isEqualTo(DealRelevance.LAW_FIRM_OR_SHAREHOLDER_ALERT);
        assertThat(insight.tradability()).isEqualTo(Tradability.NOT_TRADABLE);
    }

    @Test
    void classifiesReverseTakeoverBeforePublicStockMerger() {
        NewsArticleEntity article = article(
                "Goldflare Announces Proposed Reverse Takeover with Quitovac Gold Holdings, LLC",
                "Goldflare entered into a non-binding letter of intent for a reverse takeover. "
                        + "The proposed transaction is subject to shareholder approval and regulatory approval. "
                        + "Trading has been halted and a definitive agreement is expected later."
        );
        DetectedEventEntity event = event(article, "GOFL", CandidateStrength.HIGH, ReviewStatus.MANUAL_REVIEW);

        DealRelevanceService.RelevanceInsight insight = service.assess(article, event, likelyDeal(), terms(
                "Quitovac Gold Holdings, LLC",
                "Goldflare",
                null,
                PaymentType.STOCK,
                DealStatus.PROPOSAL
        ));

        assertThat(insight.dealRelevance()).isEqualTo(DealRelevance.REVERSE_TAKEOVER);
        assertThat(insight.dealRelevance()).isNotEqualTo(DealRelevance.PUBLIC_STOCK_MERGER);
        assertThat(insight.tradability()).isIn(Tradability.LOW, Tradability.NOT_TRADABLE);
        assertThat(insight.relevanceWarnings()).contains(
                "reverse takeover / RTO",
                "non-binding LOI",
                "definitive agreement not signed",
                "trading halted",
                "shareholder/regulatory approvals required",
                "not take-private",
                "no cash offer"
        );
    }

    @Test
    void classifiesWeakUnknownArticleAsNotTradable() {
        NewsArticleEntity article = article(
                "Company Announces Corporate Update",
                "No offer price or public target ticker was announced."
        );

        DealRelevanceService.RelevanceInsight insight = service.assess(article, null, unknownInsight(), terms(
                null,
                null,
                null,
                PaymentType.UNKNOWN,
                DealStatus.UNKNOWN
        ));

        assertThat(insight.dealRelevance()).isIn(DealRelevance.UNKNOWN, DealRelevance.NOT_TRADABLE);
        assertThat(insight.tradability()).isIn(Tradability.UNKNOWN, Tradability.NOT_TRADABLE);
    }

    @Test
    void classifiesRoundupArticleAsNotTradable() {
        NewsArticleEntity article = article(
                "13 Press Releases You Need to See This Week",
                "This weekly roundup includes companies that entered into definitive agreements."
        );

        DealRelevanceService.RelevanceInsight insight = service.assess(article, null, likelyDeal(), terms(
                null,
                null,
                null,
                PaymentType.UNKNOWN,
                DealStatus.UNKNOWN
        ));

        assertThat(insight.dealRelevance()).isEqualTo(DealRelevance.NOT_TRADABLE);
        assertThat(insight.tradability()).isEqualTo(Tradability.NOT_TRADABLE);
        assertThat(insight.relevanceWarnings()).contains("roundup/aggregator article, not primary source");
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

    private CandidateReviewInsightService.ReviewInsight unknownInsight() {
        return new CandidateReviewInsightService.ReviewInsight(
                ReviewVerdict.UNKNOWN,
                "No clear pattern.",
                List.of(),
                List.of(),
                "Review only if relevant."
        );
    }

    private DealTermsExtractionService.DealTerms terms(
            String target,
            String buyer,
            BigDecimal price,
            PaymentType paymentType,
            DealStatus dealStatus
    ) {
        return new DealTermsExtractionService.DealTerms(
                target,
                buyer,
                price,
                price == null ? null : "USD",
                paymentType,
                dealStatus,
                price == null ? DealConfidence.MEDIUM : DealConfidence.HIGH,
                List.of(),
                "Test terms"
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

    private DetectedEventEntity event(
            NewsArticleEntity article,
            String ticker,
            CandidateStrength strength,
            ReviewStatus reviewStatus
    ) {
        return new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                reviewStatus,
                90,
                null,
                ticker,
                null,
                null,
                null,
                null,
                90,
                strength,
                "Matched candidate signal.",
                strength == CandidateStrength.HIGH,
                "Review test",
                "definitive agreement",
                "",
                "",
                "Test explanation"
        );
    }
}
