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
import com.parsernews.persistence.CompanyMatchConfidence;
import com.parsernews.persistence.DealGroupReviewEntity;
import com.parsernews.persistence.DealGroupReviewRepository;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import com.parsernews.web.SignalInboxController.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DealGroupingServiceTest {
    private DetectedEventRepository eventRepository;
    private SecFilingRepository secFilingRepository;
    private CandidateReviewInsightService reviewInsightService;
    private DealTermsExtractionService dealTermsExtractionService;
    private DealRelevanceService dealRelevanceService;
    private DealStageDetectionService dealStageDetectionService;
    private AlertEligibilityService alertEligibilityService;
    private DealGroupReviewRepository dealGroupReviewRepository;
    private DealGroupingService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(DetectedEventRepository.class);
        secFilingRepository = mock(SecFilingRepository.class);
        reviewInsightService = mock(CandidateReviewInsightService.class);
        dealTermsExtractionService = mock(DealTermsExtractionService.class);
        dealRelevanceService = mock(DealRelevanceService.class);
        dealStageDetectionService = mock(DealStageDetectionService.class);
        alertEligibilityService = mock(AlertEligibilityService.class);
        dealGroupReviewRepository = mock(DealGroupReviewRepository.class);
        when(reviewInsightService.insight(any(), any())).thenReturn(new CandidateReviewInsightService.ReviewInsight(
                ReviewVerdict.LIKELY_DEAL,
                "Likely public deal.",
                List.of(),
                List.of("definitive agreement"),
                "Review deal."
        ));
        when(dealTermsExtractionService.extract(any(), any(), any())).thenAnswer(invocation -> {
            DetectedEventEntity event = invocation.getArgument(1);
            return new DealTermsExtractionService.DealTerms(
                    event.getTargetCompany(),
                    event.getAcquirer(),
                    null,
                    null,
                    PaymentType.CASH,
                    DealStatus.DEFINITIVE_AGREEMENT,
                    DealConfidence.HIGH,
                    List.of(),
                    "Extracted deterministic deal terms."
            );
        });
        when(dealRelevanceService.assess(any(), any(), any(), any())).thenReturn(new DealRelevanceService.RelevanceInsight(
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH,
                "Public target cash acquisition.",
                List.of(),
                List.of("public target")
        ));
        when(dealStageDetectionService.detect(any(), any(), any(), any(), any())).thenReturn(new DealStageDetectionService.StageInsight(
                DealStage.DEFINITIVE_AGREEMENT,
                DealTiming.EARLY,
                "Early definitive agreement.",
                List.of(),
                List.of("definitive agreement")
        ));
        when(alertEligibilityService.evaluate(any(DetectedEventEntity.class))).thenReturn(new AlertEligibilityService.AlertEligibility(
                true,
                "Strategy-eligible public target candidate."
        ));
        service = new DealGroupingService(
                eventRepository,
                secFilingRepository,
                reviewInsightService,
                dealTermsExtractionService,
                dealRelevanceService,
                dealStageDetectionService,
                alertEligibilityService,
                dealGroupReviewRepository
        );
    }

    @Test
    void groupsTwoAbbVieApogeeRssSignalsByTargetTicker() {
        DetectedEventEntity first = rssEvent(1L, "AbbVie to Acquire Apogee", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        DetectedEventEntity second = rssEvent(2L, "Apogee Enters Merger Agreement with AbbVie", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(first, second));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of());

        List<DealGroupingService.DealGroupResponse> groups = service.groups(null, null, 50);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().targetTicker()).isEqualTo("APGE");
        assertThat(groups.getFirst().buyerTicker()).isEqualTo("ABBV");
        assertThat(groups.getFirst().relatedSignals()).hasSize(2);
    }

    @Test
    void groupsAbbVieApogeeRssAndTargetSecFilingByTargetCik() {
        DetectedEventEntity rss = rssEvent(1L, "AbbVie to Acquire Apogee", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        SecFilingEntity sec = secFiling(10L, "0001974640", "APOGEE THERAPEUTICS INC", "8-K", "Agreement and plan of merger with AbbVie Inc.");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rss));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(sec));

        List<DealGroupingService.DealGroupResponse> groups = service.groups(null, null, 50);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().relatedSignals()).extracting(DealGroupingService.RelatedSignalResponse::sourceType)
                .containsExactlyInAnyOrder(SourceType.RSS_NEWS, SourceType.SEC_FILING);
        assertThat(groups.getFirst().relatedSignals()).extracting(DealGroupingService.RelatedSignalResponse::relatedReason)
                .contains("same target CIK");
    }

    @Test
    void groupsAbbVieApogeeRssTargetSecAndBuyerSecEvidenceTogether() {
        DetectedEventEntity rss = rssEvent(1L, "AbbVie to Acquire Apogee", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        SecFilingEntity targetSec = secFiling(10L, "0001974640", "APOGEE THERAPEUTICS INC", "8-K", "Agreement and plan of merger with AbbVie Inc.");
        SecFilingEntity buyerSec = secFiling(11L, "0001551152", "ABBVIE INC.", "8-K", "AbbVie entered into an agreement and plan of merger to acquire Apogee Therapeutics.");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rss));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(targetSec, buyerSec));

        List<DealGroupingService.DealGroupResponse> groups = service.groups(null, null, 50);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().targetTicker()).isEqualTo("APGE");
        assertThat(groups.getFirst().targetCik()).isEqualTo("1974640");
        assertThat(groups.getFirst().buyerTicker()).isEqualTo("ABBV");
        assertThat(groups.getFirst().buyerCik()).isEqualTo("1551152");
        assertThat(groups.getFirst().relatedSignals()).hasSize(3);
        assertThat(groups.getFirst().relatedSignals()).extracting(DealGroupingService.RelatedSignalResponse::relatedReason)
                .contains("same buyer CIK and SEC document mentions target");
    }

    @Test
    void doesNotCopyPublicBuyerTickerAndCikIntoTargetRole() {
        DetectedEventEntity badRole = rssEvent(1L, "ECARX to Acquire Flyme Auto Software Business", "ECARX Holdings Inc.", "Flyme Auto software business", "ECX", "ECX", "0001861974");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(badRole));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of());

        DealGroupingService.DealGroupResponse group = service.groups(null, null, 50).getFirst();

        assertThat(group.buyerTicker()).isEqualTo("ECX");
        assertThat(group.buyerCik()).isEqualTo("1551152");
        assertThat(group.targetTicker()).isNull();
        assertThat(group.targetCik()).isNull();
    }

    @Test
    void nearDuplicateRssTitlesWithoutResolvedTargetGroupTogetherByNormalizedTitle() {
        DetectedEventEntity first = rssEvent(1L, "True Green Announces Definitive Agreement", null, null, null, null, null);
        DetectedEventEntity second = rssEvent(2L, "True Green Announces Definitive Agreement", null, null, null, null, null);
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(first, second));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of());

        List<DealGroupingService.DealGroupResponse> groups = service.groups(null, null, 50);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().relatedSignals()).hasSize(2);
    }

    @Test
    void buyerOnlySecMatchDoesNotGroupUnrelatedDeals() {
        DetectedEventEntity rss = rssEvent(1L, "AbbVie to Acquire Apogee", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        SecFilingEntity unrelatedBuyerFiling = secFiling(11L, "0001551152", "ABBVIE INC.", "8-K", "AbbVie announces another acquisition of OtherCo.");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rss));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(unrelatedBuyerFiling));

        List<DealGroupingService.DealGroupResponse> groups = service.groups(null, null, 50);

        assertThat(groups).hasSize(2);
        assertThat(groups).anyMatch(group -> group.relatedSignals().size() == 1
                && group.relatedSignals().getFirst().sourceType() == SourceType.RSS_NEWS);
        assertThat(groups).anyMatch(group -> group.relatedSignals().size() == 1
                && group.relatedSignals().getFirst().sourceType() == SourceType.SEC_FILING);
    }

    @Test
    void filtersGroupsByPriorityAndReviewStatus() {
        DetectedEventEntity ignored = rssEvent(1L, "Ignored deal", "Buyer", "Target", "BUY", "TGT", "123");
        ignored.updateManualReview(ManualReviewStatus.IGNORED, null, "Duplicate");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(ignored));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of());

        assertThat(service.groups(null, null, 50)).isEmpty();
        assertThat(service.groups(ManualReviewStatus.IGNORED, null, 50)).hasSize(1);
        assertThat(service.groups(ManualReviewStatus.IGNORED, com.parsernews.web.SignalInboxController.UnifiedPriority.HIGH, 50)).hasSize(1);
    }

    @Test
    void persistedGroupReviewOverridesDerivedPrimaryReview() {
        DetectedEventEntity rss = rssEvent(1L, "AbbVie to Acquire Apogee", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        DealGroupReviewEntity review = new DealGroupReviewEntity("target-cik:1974640");
        review.updateManualReview(ManualReviewStatus.USEFUL, com.parsernews.persistence.ManualReviewReason.GOOD_SIGNAL, "Reviewed as one deal");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rss));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of());
        when(dealGroupReviewRepository.findByGroupKey("target-cik:1974640")).thenReturn(java.util.Optional.of(review));

        DealGroupingService.DealGroupResponse group = service.groups(null, null, 50).getFirst();

        assertThat(group.reviewStatus()).isEqualTo(ManualReviewStatus.USEFUL);
        assertThat(group.reviewReason()).isEqualTo(com.parsernews.persistence.ManualReviewReason.GOOD_SIGNAL);
        assertThat(group.reviewNote()).isEqualTo("Reviewed as one deal");
        assertThat(group.groupReviewStored()).isTrue();
    }

    @Test
    void resetGroupReviewReturnsPendingAndClearsReason() {
        DetectedEventEntity rss = rssEvent(1L, "AbbVie to Acquire Apogee", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        DealGroupReviewEntity review = new DealGroupReviewEntity("target-cik:1974640");
        review.updateManualReview(ManualReviewStatus.PENDING, com.parsernews.persistence.ManualReviewReason.GOOD_SIGNAL, "Reset me");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rss));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of());
        when(dealGroupReviewRepository.findByGroupKey("target-cik:1974640")).thenReturn(java.util.Optional.of(review));

        DealGroupingService.DealGroupResponse group = service.groups(null, null, 50).getFirst();

        assertThat(group.reviewStatus()).isEqualTo(ManualReviewStatus.PENDING);
        assertThat(group.reviewReason()).isNull();
        assertThat(group.reviewNote()).isNull();
        assertThat(group.reviewedAt()).isNull();
        assertThat(group.groupReviewStored()).isTrue();
    }

    @Test
    void telegramPreviewIncludesRssAndSecEvidence() {
        DetectedEventEntity rss = rssEvent(1L, "AbbVie to Acquire Apogee", "AbbVie Inc.", "Apogee Therapeutics", "ABBV", "APGE", "1974640");
        SecFilingEntity sec = secFiling(10L, "0001974640", "APOGEE THERAPEUTICS INC", "8-K", "Agreement and plan of merger with AbbVie Inc.");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rss));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(sec));

        DealGroupingService.DealGroupResponse group = service.groups(null, null, 50).getFirst();
        String preview = service.formatTelegramPreview(group);

        assertThat(preview).contains("M&amp;A Signal");
        assertThat(preview).contains("AbbVie");
        assertThat(preview).contains("Apogee");
        assertThat(preview).contains("$APGE");
        assertThat(preview).contains("Triggered by");
    }

    // SEC-sourced groups usually carry no ticker (the EDGAR feed omits it), so the alert showed no
    // ticker, price or chart even when the AI review had resolved one against SEC's company list.
    @Test
    void telegramPreviewUsesTheResolvedTickerWhenTheGroupHasNone() {
        SecFilingEntity sec = secFiling(10L, "0001806201", "OPEN LENDING CORP", "SC 14D9", "Tender offer statement");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of());
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(sec));

        DealGroupingService.DealGroupResponse group = service.groups(null, null, 50).getFirst();

        assertThat(group.targetTicker()).isNull();
        assertThat(service.formatTelegramPreview(group)).doesNotContain("$LPRO");
        assertThat(service.formatTelegramPreview(group, "LPRO")).contains("$LPRO");
    }

    private DetectedEventEntity rssEvent(
            Long id,
            String headline,
            String buyer,
            String target,
            String buyerTicker,
            String targetTicker,
            String targetCik
    ) {
        NewsSourceEntity source = new NewsSourceEntity("PRNewswire", NewsSourceType.RSS, "https://example.test/rss");
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "hash-" + id,
                targetTicker,
                target,
                headline,
                headline + " under a definitive agreement.",
                "https://example.test/news/" + id,
                Instant.parse("2026-06-20T12:00:00Z").plusSeconds(id)
        );
        setId(article, id);
        DetectedEventEntity event = new DetectedEventEntity(
                article,
                DetectedEventType.ACQUISITION,
                ReviewStatus.HIGH_PRIORITY_SIGNAL,
                90,
                target,
                targetTicker,
                buyer,
                "$10.00",
                "cash",
                "40%",
                90,
                CandidateStrength.HIGH,
                "Matched HIGH deal signal.",
                true,
                "Eligible",
                "definitive agreement",
                "",
                "",
                "Detected as M&A candidate."
        );
        setId(event, id);
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

    private SecFilingEntity secFiling(Long id, String cik, String companyName, String form, String snippet) {
        SecFilingEntity filing = new SecFilingEntity(
                cik,
                companyName,
                form,
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
}
