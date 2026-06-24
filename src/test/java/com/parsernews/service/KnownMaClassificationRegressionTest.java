package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.ReviewVerdict;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.CompanyMatchConfidence;
import com.parsernews.persistence.DealGroupReviewRepository;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import com.parsernews.web.SignalInboxController.SourceType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnownMaClassificationRegressionTest {
    private final CandidateReviewInsightService reviewInsightService = new CandidateReviewInsightService();
    private final DealTermsExtractionService dealTermsExtractionService = new DealTermsExtractionService();
    private final DealRelevanceService dealRelevanceService = new DealRelevanceService();
    private final DealStageDetectionService dealStageDetectionService = new DealStageDetectionService();
    private final AlertEligibilityService alertEligibilityService = new AlertEligibilityService(
            reviewInsightService,
            dealTermsExtractionService,
            dealRelevanceService,
            dealStageDetectionService
    );

    @Test
    void abbVieApogeePrimaryRssSignalStaysHighTradabilityPublicCashAcquisition() {
        DetectedEventEntity event = event(
                1L,
                "AbbVie to Acquire Apogee Therapeutics for $8.8 Billion",
                "AbbVie Inc. (NYSE: ABBV) and Apogee Therapeutics, Inc. (NASDAQ: APGE) announced a definitive agreement "
                        + "under which AbbVie will acquire Apogee. Apogee shareholders will receive $56.00 per share in cash.",
                "https://www.prnewswire.com/news-releases/abbvie-to-acquire-apogee-302000001.html",
                "APGE",
                "Apogee Therapeutics",
                "AbbVie Inc.",
                CandidateStrength.HIGH,
                "definitive agreement|per share in cash|will acquire|shareholders will receive"
        );
        event.updateCompanyEnrichment(
                "APGE",
                "1550760",
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                "ABBV",
                "1551152",
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                null
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(event.getCandidateStrength()).isEqualTo(CandidateStrength.HIGH);
        assertThat(classification.reviewInsight.reviewVerdict()).isEqualTo(ReviewVerdict.LIKELY_DEAL);
        assertThat(classification.relevance.dealRelevance()).isEqualTo(DealRelevance.PUBLIC_CASH_ACQUISITION);
        assertThat(classification.relevance.tradability()).isEqualTo(Tradability.HIGH);
        assertThat(classification.stage.dealStage()).isIn(DealStage.INITIAL_ANNOUNCEMENT, DealStage.DEFINITIVE_AGREEMENT);
        assertThat(classification.stage.dealTiming()).isEqualTo(DealTiming.EARLY);
        assertThat(eligibility.eligible()).isTrue();
    }

    @Test
    void duplicateAbbVieApogeePrNewswireReleasesCollapseIntoOneDealGroup() {
        DetectedEventEntity first = groupedRssEvent(1L, "AbbVie to Acquire Apogee Therapeutics", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1550760");
        DetectedEventEntity second = groupedRssEvent(2L, "Apogee Enters Definitive Merger Agreement with AbbVie", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1550760");
        DealGroupingService service = groupingService(List.of(first, second), List.of());

        List<DealGroupingService.DealGroupResponse> groups = service.groups(null, null, 50);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().targetTicker()).isEqualTo("APGE");
        assertThat(groups.getFirst().relatedSignals()).hasSizeGreaterThan(1);
    }

    @Test
    void abbVieApogeeRssAndSecEvidenceGroupTogetherByTargetCik() {
        DetectedEventEntity rss = groupedRssEvent(1L, "AbbVie to Acquire Apogee Therapeutics", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1550760");
        SecFilingEntity sec = secFiling(10L, "0001550760", "APOGEE THERAPEUTICS INC", "Agreement and plan of merger with AbbVie Inc.");
        DealGroupingService service = groupingService(List.of(rss), List.of(sec));

        DealGroupingService.DealGroupResponse group = service.groups(null, null, 50).getFirst();

        assertThat(group.relatedSignals()).extracting(DealGroupingService.RelatedSignalResponse::sourceType)
                .containsExactlyInAnyOrder(SourceType.RSS_NEWS, SourceType.SEC_FILING);
        assertThat(group.relatedSignals()).extracting(DealGroupingService.RelatedSignalResponse::relatedReason)
                .contains("same target CIK");
    }

    @Test
    void tdacRedemptionAndExtensionVoteIsNotAnAlertableGoodSignal() {
        DetectedEventEntity event = event(
                2L,
                "TDAC Announces Redemption Deadline and Extension Vote",
                "Translational Development Acquisition Corp. reminds shareholders of the redemption deadline "
                        + "for its extension vote and special meeting. This is not an acquisition agreement.",
                "https://www.prnewswire.com/news-releases/tdac-extension-vote-302000002.html",
                "TDAC",
                "TDAC",
                null,
                CandidateStrength.LOW,
                "extension vote|redemption deadline"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(classification.relevance.dealRelevance()).isNotEqualTo(DealRelevance.PUBLIC_CASH_ACQUISITION);
        assertThat(classification.relevance.tradability()).isIn(Tradability.NOT_TRADABLE, Tradability.UNKNOWN);
        assertThat(classification.reviewInsight.suggestedAction()).doesNotContain("GOOD_SIGNAL");
    }

    @Test
    void ecarxFlymeBusinessUnitDoesNotBecomePublicTargetFromPublicBuyer() {
        DetectedEventEntity event = event(
                3L,
                "ECARX to Acquire Flyme Auto Software Business",
                "ECARX Holdings Inc. (NASDAQ: ECX) will acquire the Flyme Auto software business from DreamSmart. "
                        + "Financial terms were not disclosed.",
                "https://www.globenewswire.com/news-release/ecarx-flyme.html",
                "UNKNOWN",
                "Flyme Auto software business",
                "ECARX Holdings Inc.",
                CandidateStrength.HIGH,
                "will acquire"
        );
        event.updateCompanyEnrichment(
                null,
                null,
                false,
                CompanyMatchConfidence.NONE,
                "ECX",
                "0001861974",
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                "buyer resolved but target not resolved"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isIn(DealRelevance.PRIVATE_COMPANY_ACQUISITION, DealRelevance.NOT_TRADABLE, DealRelevance.UNKNOWN);
        assertThat(classification.relevance.tradability()).isIn(Tradability.NOT_TRADABLE, Tradability.UNKNOWN, Tradability.LOW);
        assertThat(classification.relevance.relevanceWarnings()).contains("do not infer public target from public buyer");
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void bristowBerryPublicBuyerDoesNotBecomePublicTarget() {
        DetectedEventEntity event = publicBuyerPrivateTargetEvent(
                31L,
                "Bristow Group to Acquire Berry Aviation",
                "Bristow Group Inc. (NYSE: VTOL) will acquire Berry Aviation, a privately held aviation services provider. Terms were not disclosed.",
                "VTOL",
                "Bristow Group Inc.",
                "Berry Aviation"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isIn(DealRelevance.PRIVATE_COMPANY_ACQUISITION, DealRelevance.NOT_TRADABLE);
        assertThat(classification.relevance.tradability()).isIn(Tradability.NOT_TRADABLE, Tradability.LOW);
        assertThat(classification.relevance.dealRelevance()).isNotEqualTo(DealRelevance.PUBLIC_CASH_ACQUISITION);
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void energyFuelsVacPublicBuyerDoesNotBecomePublicTarget() {
        DetectedEventEntity event = publicBuyerPrivateTargetEvent(
                32L,
                "Energy Fuels to Acquire VAC Business",
                "Energy Fuels Inc. (NYSE American: UUUU) announced it will acquire the VAC rare earth separation business. Terms were not disclosed.",
                "UUUU",
                "Energy Fuels Inc.",
                "VAC rare earth separation business"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isIn(DealRelevance.PRIVATE_COMPANY_ACQUISITION, DealRelevance.NOT_TRADABLE);
        assertThat(classification.relevance.tradability()).isIn(Tradability.NOT_TRADABLE, Tradability.LOW);
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void firstCashRamsdensPublicBuyerDoesNotBecomePublicTarget() {
        DetectedEventEntity event = publicBuyerPrivateTargetEvent(
                33L,
                "FirstCash to Acquire Ramsdens",
                "FirstCash Holdings, Inc. (NASDAQ: FCFS) announced an agreement to acquire Ramsdens. The target public ticker was not provided.",
                "FCFS",
                "FirstCash Holdings, Inc.",
                "Ramsdens"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isIn(DealRelevance.PRIVATE_COMPANY_ACQUISITION, DealRelevance.NOT_TRADABLE, DealRelevance.UNKNOWN);
        assertThat(classification.relevance.dealRelevance()).isNotEqualTo(DealRelevance.PUBLIC_CASH_ACQUISITION);
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void mackayComstockMiningAssetsStayNotTradableAssetAcquisition() {
        DetectedEventEntity event = event(
                4L,
                "Mackay Announces Acquisition of Comstock Mining Assets",
                "Mackay has entered into an agreement to acquire certain mining assets from Comstock. "
                        + "The transaction is an asset acquisition and does not include a public target cash offer.",
                "https://www.globenewswire.com/news-release/mackay-comstock-assets.html",
                "UNKNOWN",
                "Comstock mining assets",
                "Mackay",
                CandidateStrength.MEDIUM,
                "asset acquisition|agreement to acquire"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isNotEqualTo(DealRelevance.PUBLIC_CASH_ACQUISITION);
        assertThat(classification.relevance.tradability()).isIn(Tradability.NOT_TRADABLE, Tradability.UNKNOWN, Tradability.LOW);
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void gdCultureRegisteredDirectOfferingIsNotPublicStockMerger() {
        DetectedEventEntity event = event(
                41L,
                "GD Culture Group Announces Registered Direct Offering and Private Placement",
                "GD Culture Group entered into a securities purchase agreement for a registered direct offering and concurrent private placement.",
                "https://www.prnewswire.com/news-releases/gdc-offering.html",
                "GDC",
                "GD Culture Group",
                null,
                CandidateStrength.HIGH,
                "definitive agreement|common stock"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isEqualTo(DealRelevance.NOT_TRADABLE);
        assertThat(classification.relevance.dealRelevance()).isNotEqualTo(DealRelevance.PUBLIC_STOCK_MERGER);
        assertThat(classification.relevance.relevanceWarnings()).contains("financing/debt/offering event");
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void privatePlacementIsNotMa() {
        DetectedEventEntity event = event(
                42L,
                "Company Announces Private Placement Financing",
                "The company announced a private placement of common stock and warrants.",
                "https://www.globenewswire.com/news-release/private-placement.html",
                "TEST",
                "Company",
                null,
                CandidateStrength.MEDIUM,
                "private placement"
        );

        Classification classification = classify(event);

        assertThat(classification.relevance.dealRelevance()).isEqualTo(DealRelevance.NOT_TRADABLE);
        assertThat(classification.relevance.tradability()).isEqualTo(Tradability.NOT_TRADABLE);
    }

    @Test
    void seniorNotesTenderOfferIsNotMa() {
        DetectedEventEntity event = event(
                43L,
                "Company Launches Tender Offer for Senior Notes",
                "The company launched a tender offer for senior notes and debt securities.",
                "https://www.prnewswire.com/news-releases/senior-notes-tender.html",
                "TEST",
                "Company",
                null,
                CandidateStrength.HIGH,
                "tender offer"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isEqualTo(DealRelevance.NOT_TRADABLE);
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void mdaBlueCanyonPublicBuyerPrivateTargetStaysNotTradable() {
        DetectedEventEntity event = event(
                5L,
                "MDA Space announces definitive agreement to acquire US-based Blue Canyon Technologies LLC",
                "MDA Space Ltd. (TSX:MDA) will acquire Blue Canyon Technologies LLC. "
                        + "The transaction is expected to close in Q4 subject to customary closing conditions, including regulatory approvals.",
                "https://www.globenewswire.com/news-release/mda-blue-canyon.html",
                "MDA",
                "Blue Canyon Technologies LLC",
                "MDA Space",
                CandidateStrength.HIGH,
                "definitive agreement|to acquire"
        );
        event.updateCompanyEnrichment(
                null,
                null,
                false,
                CompanyMatchConfidence.NONE,
                "MDA",
                "0000000000",
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                "buyer resolved but target not resolved"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isEqualTo(DealRelevance.PRIVATE_COMPANY_ACQUISITION);
        assertThat(classification.relevance.tradability()).isIn(Tradability.LOW, Tradability.NOT_TRADABLE);
        assertThat(classification.relevance.relevanceWarnings()).contains("do not infer public target from public buyer");
        assertThat(classification.stage.dealTiming()).isEqualTo(DealTiming.EARLY);
        assertThat(eligibility.eligible()).isFalse();
    }

    @Test
    void goldflareReverseTakeoverStaysNotTradableAndNotAlertEligible() {
        DetectedEventEntity event = event(
                6L,
                "Goldflare Announces Proposed Reverse Takeover with Quitovac Gold Holdings, LLC",
                "Goldflare entered into a non-binding letter of intent for a reverse takeover. "
                        + "Trading has been halted and a definitive agreement is expected later. "
                        + "The proposed transaction is subject to shareholder approval and regulatory approval.",
                "https://www.globenewswire.com/news-release/goldflare-rto.html",
                "GOFL",
                "Quitovac Gold Holdings, LLC",
                "Goldflare",
                CandidateStrength.HIGH,
                "reverse takeover|non-binding letter of intent"
        );

        Classification classification = classify(event);
        AlertEligibilityService.AlertEligibility eligibility = alertEligibilityService.evaluate(event);

        assertThat(classification.relevance.dealRelevance()).isEqualTo(DealRelevance.REVERSE_TAKEOVER);
        assertThat(classification.relevance.tradability()).isIn(Tradability.LOW, Tradability.NOT_TRADABLE);
        assertThat(classification.stage.dealStage()).isNotEqualTo(DealStage.SHAREHOLDER_APPROVAL);
        assertThat(classification.stage.dealTiming()).isNotEqualTo(DealTiming.LATE_STAGE);
        assertThat(eligibility.eligible()).isFalse();
    }

    private Classification classify(DetectedEventEntity event) {
        CandidateReviewInsightService.ReviewInsight reviewInsight = reviewInsightService.insight(event.getArticle(), event);
        DealTermsExtractionService.DealTerms terms = dealTermsExtractionService.extract(event.getArticle(), event, reviewInsight);
        DealRelevanceService.RelevanceInsight relevance = dealRelevanceService.assess(event.getArticle(), event, reviewInsight, terms);
        DealStageDetectionService.StageInsight stage = dealStageDetectionService.detect(event.getArticle(), event, terms, reviewInsight, relevance);
        return new Classification(reviewInsight, terms, relevance, stage);
    }

    private DealGroupingService groupingService(List<DetectedEventEntity> events, List<SecFilingEntity> filings) {
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        SecFilingRepository secFilingRepository = mock(SecFilingRepository.class);
        DealGroupReviewRepository reviewRepository = mock(DealGroupReviewRepository.class);
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(events);
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(filings);
        when(reviewRepository.findByGroupKey(any())).thenReturn(java.util.Optional.empty());
        return new DealGroupingService(
                eventRepository,
                secFilingRepository,
                reviewInsightService,
                dealTermsExtractionService,
                dealRelevanceService,
                dealStageDetectionService,
                reviewRepository
        );
    }

    private DetectedEventEntity groupedRssEvent(
            Long id,
            String headline,
            String buyer,
            String target,
            String buyerTicker,
            String targetTicker,
            String targetCik
    ) {
        DetectedEventEntity event = event(
                id,
                headline,
                headline + " under a definitive agreement. Shareholders will receive cash consideration.",
                "https://www.prnewswire.com/news-releases/" + id + ".html",
                targetTicker,
                target,
                buyer,
                CandidateStrength.HIGH,
                "definitive agreement|cash consideration"
        );
        event.updateCompanyEnrichment(
                targetTicker,
                targetCik,
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                buyerTicker,
                "1551152",
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                null
        );
        return event;
    }

    private DetectedEventEntity publicBuyerPrivateTargetEvent(
            Long id,
            String headline,
            String body,
            String buyerTicker,
            String buyerCompany,
            String targetCompany
    ) {
        DetectedEventEntity event = event(
                id,
                headline,
                body,
                "https://www.prnewswire.com/news-releases/" + id + ".html",
                buyerTicker,
                targetCompany,
                buyerCompany,
                CandidateStrength.HIGH,
                "will acquire"
        );
        event.updateCompanyEnrichment(
                null,
                null,
                false,
                CompanyMatchConfidence.NONE,
                buyerTicker,
                "999999",
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                "buyer resolved but target not resolved; do not infer public target from public buyer"
        );
        return event;
    }

    private DetectedEventEntity event(
            Long id,
            String headline,
            String body,
            String url,
            String ticker,
            String targetCompany,
            String acquirer,
            CandidateStrength strength,
            String positiveKeywords
    ) {
        NewsArticleEntity article = new NewsArticleEntity(
                new NewsSourceEntity("Regression News", NewsSourceType.RSS, "https://www.prnewswire.com/rss"),
                "hash-" + id,
                ticker,
                targetCompany == null ? "UNKNOWN" : targetCompany,
                headline,
                body,
                url,
                Instant.parse("2026-06-20T12:00:00Z").plusSeconds(id)
        );
        setId(article, id);
        DetectedEventEntity event = new DetectedEventEntity(
                article,
                DetectedEventType.ACQUISITION,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                strength == CandidateStrength.HIGH ? 90 : 45,
                targetCompany,
                ticker,
                acquirer,
                null,
                null,
                null,
                strength == CandidateStrength.HIGH ? 90 : 45,
                strength,
                "Regression fixture",
                strength == CandidateStrength.HIGH,
                "Regression fixture",
                positiveKeywords,
                "",
                "",
                "Regression fixture"
        );
        setId(event, id);
        return event;
    }

    private SecFilingEntity secFiling(Long id, String cik, String companyName, String snippet) {
        SecFilingEntity filing = new SecFilingEntity(
                cik,
                companyName,
                "8-K",
                LocalDate.of(2026, 6, 20),
                cik + "-26-000001",
                "filing.htm",
                "https://www.sec.gov/Archives/edgar/data/" + cik.replaceFirst("^0+", "") + "/filing.htm",
                "WATCHLIST_FORM",
                "Interesting SEC filing."
        );
        setId(filing, id);
        filing.markDocumentFetched(
                filing.getFilingUrl(),
                snippet,
                "HIGH",
                "SEC document signal.",
                SecSignalType.MERGER_AGREEMENT,
                SecSignalPriority.HIGH,
                "SEC document signal.",
                null
        );
        return filing;
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

    private record Classification(
            CandidateReviewInsightService.ReviewInsight reviewInsight,
            DealTermsExtractionService.DealTerms terms,
            DealRelevanceService.RelevanceInsight relevance,
            DealStageDetectionService.StageInsight stage
    ) {
    }
}
