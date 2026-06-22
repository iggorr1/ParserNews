package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.config.TelegramAlertSettings;
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
import com.parsernews.service.AlertNotifier;
import com.parsernews.service.SignalTelegramMessageFormatter;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private SignalTelegramMessageFormatter signalTelegramMessageFormatter;

    @MockitoBean
    private AlertNotifier alertNotifier;

    @MockitoBean
    private TelegramAlertSettings telegramAlertSettings;

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
        when(signalTelegramMessageFormatter.formatRss(any())).thenReturn("RSS telegram message");
        when(signalTelegramMessageFormatter.formatSec(any())).thenReturn("SEC telegram message");
        when(telegramAlertSettings.enabled()).thenReturn(false);
        when(telegramAlertSettings.botToken()).thenReturn("");
        when(telegramAlertSettings.chatId()).thenReturn("");
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

    @Test
    void rssSignalDetailsReturnEvidenceFields() throws Exception {
        when(eventRepository.findById(1L)).thenReturn(java.util.Optional.of(rssEvent(CandidateStrength.HIGH, true)));

        mockMvc.perform(get("/api/signals/RSS_NEWS/1").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("RSS_NEWS"))
                .andExpect(jsonPath("$.title").value("Target Enters Definitive Merger Agreement"))
                .andExpect(jsonPath("$.candidateStrength").value("HIGH"))
                .andExpect(jsonPath("$.candidateScore").value(90))
                .andExpect(jsonPath("$.candidateReason").value("Matched merger agreement."))
                .andExpect(jsonPath("$.matchedPositiveKeywords").value("merger agreement"))
                .andExpect(jsonPath("$.reviewVerdict").value("LIKELY_DEAL"))
                .andExpect(jsonPath("$.dealSummary").value("Buyer / Target, cash merger."))
                .andExpect(jsonPath("$.targetCompany").value("Target Inc."))
                .andExpect(jsonPath("$.targetTicker").value("TGT"))
                .andExpect(jsonPath("$.targetCik").value("123456"))
                .andExpect(jsonPath("$.targetPublicCompany").value(true))
                .andExpect(jsonPath("$.targetMatchConfidence").value("EXACT_TICKER"))
                .andExpect(jsonPath("$.buyerCompany").value("Buyer Inc."))
                .andExpect(jsonPath("$.buyerTicker").value("BUY"))
                .andExpect(jsonPath("$.buyerCik").value("654321"))
                .andExpect(jsonPath("$.buyerPublicCompany").value(true))
                .andExpect(jsonPath("$.buyerMatchConfidence").value("EXACT_NAME"))
                .andExpect(jsonPath("$.companyEnrichmentWarnings").value("buyer resolved but target not resolved"))
                .andExpect(jsonPath("$.dealRelevance").value("PUBLIC_CASH_ACQUISITION"))
                .andExpect(jsonPath("$.tradability").value("HIGH"))
                .andExpect(jsonPath("$.dealStage").value("DEFINITIVE_AGREEMENT"))
                .andExpect(jsonPath("$.dealTiming").value("EARLY"))
                .andExpect(jsonPath("$.alertEligible").value(true))
                .andExpect(jsonPath("$.alertEligibilityReason").value("Eligible"))
                .andExpect(jsonPath("$.manualReviewStatus").value("PENDING"));
    }

    @Test
    void secSignalDetailsReturnEvidenceFields() throws Exception {
        SecFilingEntity sec = secFiling(SecSignalPriority.HIGH, SecSignalType.TENDER_OFFER);
        when(secFilingRepository.findById(7L)).thenReturn(java.util.Optional.of(sec));

        mockMvc.perform(get("/api/signals/SEC_FILING/7").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("SEC_FILING"))
                .andExpect(jsonPath("$.companyName").value("MICROSOFT CORP"))
                .andExpect(jsonPath("$.cik").value("0000789019"))
                .andExpect(jsonPath("$.form").value("8-K"))
                .andExpect(jsonPath("$.accessionNumber").value("0000789019-26-000001"))
                .andExpect(jsonPath("$.documentFetchStatus").value("FETCHED"))
                .andExpect(jsonPath("$.documentTextSnippet").value("Document text"))
                .andExpect(jsonPath("$.documentSignalStrength").value("HIGH"))
                .andExpect(jsonPath("$.documentSignalReason").value("SEC document signal."))
                .andExpect(jsonPath("$.secSignalType").value("TENDER_OFFER"))
                .andExpect(jsonPath("$.secSignalPriority").value("HIGH"))
                .andExpect(jsonPath("$.secSignalSummary").value("SEC document signal."))
                .andExpect(jsonPath("$.manualReviewStatus").value("PENDING"));
    }

    @Test
    void signalDetailsReturn404ForUnknownId() throws Exception {
        when(eventRepository.findById(404L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/signals/RSS_NEWS/404").with(httpBasic("tester", "secret")))
                .andExpect(status().isNotFound());
    }

    @Test
    void signalDetailsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/signals/RSS_NEWS/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rssTelegramPreviewReturnsMessageText() throws Exception {
        DetectedEventEntity rss = rssEvent(CandidateStrength.HIGH, true);
        when(eventRepository.findById(1L)).thenReturn(java.util.Optional.of(rss));

        mockMvc.perform(get("/api/signals/RSS_NEWS/1/telegram-preview")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("RSS_NEWS"))
                .andExpect(jsonPath("$.messageText").value("RSS telegram message"))
                .andExpect(jsonPath("$.telegramEnabled").value(false))
                .andExpect(jsonPath("$.sendAllowed").value(false));
    }

    @Test
    void secTelegramPreviewReturnsMessageText() throws Exception {
        SecFilingEntity sec = secFiling(SecSignalPriority.HIGH, SecSignalType.TENDER_OFFER);
        when(secFilingRepository.findById(7L)).thenReturn(java.util.Optional.of(sec));

        mockMvc.perform(get("/api/signals/SEC_FILING/7/telegram-preview")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("SEC_FILING"))
                .andExpect(jsonPath("$.messageText").value("SEC telegram message"))
                .andExpect(jsonPath("$.telegramEnabled").value(false))
                .andExpect(jsonPath("$.sendAllowed").value(false));
    }

    @Test
    void sendTelegramWhenDisabledDoesNotSend() throws Exception {
        when(eventRepository.findById(1L)).thenReturn(java.util.Optional.of(rssEvent(CandidateStrength.HIGH, true)));

        mockMvc.perform(post("/api/signals/RSS_NEWS/1/send-telegram")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.telegramEnabled").value(false))
                .andExpect(jsonPath("$.telegramConfigured").value(false))
                .andExpect(jsonPath("$.message").value("Telegram is disabled; no external message will be sent."));

        verify(alertNotifier, never()).send(any());
    }

    @Test
    void sendTelegramWhenConfigMissingDoesNotSend() throws Exception {
        when(telegramAlertSettings.enabled()).thenReturn(true);
        when(eventRepository.findById(1L)).thenReturn(java.util.Optional.of(rssEvent(CandidateStrength.HIGH, true)));

        mockMvc.perform(post("/api/signals/RSS_NEWS/1/send-telegram")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.telegramEnabled").value(true))
                .andExpect(jsonPath("$.telegramConfigured").value(false))
                .andExpect(jsonPath("$.message").value("Telegram is enabled but bot token or chat id is missing."));

        verify(alertNotifier, never()).send(any());
    }

    @Test
    void sendTelegramWithMockedNotifierCanSucceed() throws Exception {
        when(telegramAlertSettings.enabled()).thenReturn(true);
        when(telegramAlertSettings.botToken()).thenReturn("token");
        when(telegramAlertSettings.chatId()).thenReturn("chat");
        when(eventRepository.findById(1L)).thenReturn(java.util.Optional.of(rssEvent(CandidateStrength.HIGH, true)));
        when(alertNotifier.send("RSS telegram message"))
                .thenReturn(AlertNotifier.AlertNotificationResult.sent("SENT", "Telegram alert message was sent."));

        mockMvc.perform(post("/api/signals/RSS_NEWS/1/send-telegram")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.telegramEnabled").value(true))
                .andExpect(jsonPath("$.telegramConfigured").value(true))
                .andExpect(jsonPath("$.message").value("Telegram alert message was sent."));

        verify(alertNotifier).send("RSS telegram message");
    }

    @Test
    void telegramEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/signals/RSS_NEWS/1/telegram-preview"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/signals/RSS_NEWS/1/send-telegram"))
                .andExpect(status().isUnauthorized());
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
        DetectedEventEntity event = new DetectedEventEntity(
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
        event.updateCompanyEnrichment(
                "TGT",
                "123456",
                true,
                CompanyMatchConfidence.EXACT_TICKER,
                "BUY",
                "654321",
                true,
                CompanyMatchConfidence.EXACT_NAME,
                "buyer resolved but target not resolved"
        );
        return event;
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
