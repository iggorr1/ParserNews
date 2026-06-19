package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
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
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.persistence.SecSignalPriority;
import com.parsernews.persistence.SecSignalType;
import com.parsernews.service.CandidateReviewInsightService;
import com.parsernews.service.DealRelevanceService;
import com.parsernews.service.DealStageDetectionService;
import com.parsernews.service.DealTermsExtractionService;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SignalInboxController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class SignalInboxControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectedEventRepository eventRepository;

    @MockitoBean
    private SecFilingRepository secFilingRepository;

    @MockitoBean
    private CandidateReviewInsightService reviewInsightService;

    @MockitoBean
    private DealTermsExtractionService dealTermsExtractionService;

    @MockitoBean
    private DealRelevanceService dealRelevanceService;

    @MockitoBean
    private DealStageDetectionService dealStageDetectionService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @BeforeEach
    void setUpAnalysisMocks() {
        when(reviewInsightService.insight(any(), any())).thenReturn(new CandidateReviewInsightService.ReviewInsight(
                ReviewVerdict.LIKELY_DEAL,
                "Strong RSS deal signal.",
                List.of(),
                List.of("merger agreement"),
                "Review source."
        ));
        when(dealTermsExtractionService.extract(any(), any(), any())).thenReturn(new DealTermsExtractionService.DealTerms(
                "Target Inc.",
                "Buyer Inc.",
                null,
                null,
                PaymentType.CASH,
                DealStatus.MERGER_AGREEMENT,
                DealConfidence.HIGH,
                List.of(),
                "Buyer / Target, cash merger."
        ));
        when(dealRelevanceService.assess(any(), any(), any(), any())).thenReturn(new DealRelevanceService.RelevanceInsight(
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH,
                "Public cash deal.",
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
    }

    @Test
    void signalsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/signals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsRssAndSecSignalsSortedByImportance() throws Exception {
        DetectedEventEntity rss = rssEvent(CandidateStrength.HIGH, true);
        SecFilingEntity sec = secFiling(SecSignalPriority.MEDIUM, SecSignalType.BUSINESS_COMBINATION);
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rss));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(sec));

        mockMvc.perform(get("/api/signals").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").value("RSS_NEWS"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].alertEligible").value(true))
                .andExpect(jsonPath("$[1].sourceType").value("SEC_FILING"))
                .andExpect(jsonPath("$[1].secSignalPriority").value("MEDIUM"));
    }

    @Test
    void filtersBySourceTypePriorityAndReviewStatus() throws Exception {
        SecFilingEntity sec = secFiling(SecSignalPriority.HIGH, SecSignalType.TENDER_OFFER);
        sec.updateManualReview(ManualReviewStatus.USEFUL, ManualReviewReason.GOOD_SIGNAL, "Good SEC signal");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(rssEvent(CandidateStrength.HIGH, true)));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of(sec));

        mockMvc.perform(get("/api/signals?sourceType=SEC_FILING&priority=HIGH&reviewStatus=USEFUL")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").value("SEC_FILING"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].reviewStatus").value("USEFUL"))
                .andExpect(jsonPath("$[0].secSignalType").value("TENDER_OFFER"));
    }

    @Test
    void hidesIgnoredSignalsByDefault() throws Exception {
        DetectedEventEntity ignored = rssEvent(CandidateStrength.HIGH, true);
        ignored.updateManualReview(ManualReviewStatus.IGNORED, ManualReviewReason.FALSE_POSITIVE, "Ignore");
        when(eventRepository.findTop200ByOrderByDetectedAtDesc()).thenReturn(List.of(ignored));
        when(secFilingRepository.findTop100ByOrderByFilingDateDescProcessedAtDesc()).thenReturn(List.of());

        mockMvc.perform(get("/api/signals").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/signals?reviewStatus=IGNORED").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewStatus").value("IGNORED"));
    }

    private DetectedEventEntity rssEvent(CandidateStrength strength, boolean alertEligible) {
        NewsSourceEntity source = new NewsSourceEntity("GlobeNewswire", NewsSourceType.RSS, "https://example.test/rss");
        NewsArticleEntity article = new NewsArticleEntity(
                source,
                "hash-" + strength + "-" + alertEligible,
                "TGT",
                "Target Inc.",
                "Target Enters Definitive Merger Agreement",
                "Target enters into a definitive merger agreement.",
                "https://example.test/news/target",
                Instant.parse("2026-06-19T12:00:00Z")
        );
        return new DetectedEventEntity(
                article,
                DetectedEventType.MERGER,
                ReviewStatus.MANUAL_REVIEW,
                90,
                "Target Inc.",
                "TGT",
                "Buyer Inc.",
                "$10.00",
                "cash",
                "40%",
                strength == CandidateStrength.HIGH ? 90 : 50,
                strength,
                "Matched merger agreement.",
                alertEligible,
                "Eligible",
                "merger agreement",
                "",
                "",
                "Detected as candidate."
        );
    }

    private SecFilingEntity secFiling(SecSignalPriority priority, SecSignalType type) {
        SecFilingEntity filing = new SecFilingEntity(
                "0000789019",
                "MICROSOFT CORP",
                "8-K",
                LocalDate.of(2026, 6, 18),
                "0000789019-26-000001",
                "msft-8k.htm",
                "https://www.sec.gov/Archives/edgar/data/789019/000078901926000001/msft-8k.htm",
                "WATCHLIST_FORM",
                "Interesting SEC form."
        );
        filing.markDocumentFetched(
                filing.getFilingUrl(),
                "Document text",
                priority.name(),
                "SEC document signal.",
                type,
                priority,
                "SEC document signal.",
                null
        );
        return filing;
    }
}
