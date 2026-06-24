package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.config.TelegramAlertSettings;
import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.AlertNotifier;
import com.parsernews.service.DealGroupAiReviewService;
import com.parsernews.service.DealGroupReviewService;
import com.parsernews.service.DealGroupingService;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DealGroupController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class DealGroupControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DealGroupingService dealGroupingService;

    @MockitoBean
    private DealGroupReviewService dealGroupReviewService;

    @MockitoBean
    private DealGroupAiReviewService dealGroupAiReviewService;

    @MockitoBean
    private AlertNotifier alertNotifier;

    @MockitoBean
    private TelegramAlertSettings telegramAlertSettings;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(telegramAlertSettings.enabled()).thenReturn(false);
        when(telegramAlertSettings.botToken()).thenReturn("");
        when(telegramAlertSettings.chatId()).thenReturn("");
    }

    @Test
    void dealGroupsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/deal-groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dealGroupStatsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/deal-groups/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dealGroupsReturnGroupedSignals() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.groups(null, null, 50)).thenReturn(List.of(group));

        mockMvc.perform(get("/api/deal-groups").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupKey").value("target-ticker:APGE"))
                .andExpect(jsonPath("$[0].targetTicker").value("APGE"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].relatedSignals.length()").value(2))
                .andExpect(jsonPath("$[0].relatedSignals[0].sourceType").value("RSS_NEWS"));
    }

    @Test
    void dealGroupDetailReturnsOneGroup() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/deal-groups/target-ticker:APGE").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupKey").value("target-ticker:APGE"))
                .andExpect(jsonPath("$.relatedSignals.length()").value(2));
    }

    @Test
    void dealGroupDetailReturns404ForMissingGroup() throws Exception {
        when(dealGroupingService.group("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/deal-groups/missing").with(httpBasic("tester", "secret")))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualReviewEndpointUpdatesGroupReview() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));
        when(dealGroupReviewService.update(
                org.mockito.ArgumentMatchers.eq("target-ticker:APGE"),
                org.mockito.ArgumentMatchers.eq(ManualReviewStatus.USEFUL),
                org.mockito.ArgumentMatchers.eq(ManualReviewReason.GOOD_SIGNAL),
                org.mockito.ArgumentMatchers.eq("Reviewed group")
        )).thenReturn(new com.parsernews.persistence.DealGroupReviewEntity("target-ticker:APGE"));

        mockMvc.perform(patch("/api/deal-groups/target-ticker:APGE/manual-review")
                        .with(httpBasic("tester", "secret"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "USEFUL",
                                  "reason": "GOOD_SIGNAL",
                                  "note": "Reviewed group"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupKey").value("target-ticker:APGE"));

        verify(dealGroupReviewService).update(
                "target-ticker:APGE",
                ManualReviewStatus.USEFUL,
                ManualReviewReason.GOOD_SIGNAL,
                "Reviewed group"
        );
    }

    @Test
    void telegramPreviewReturnsGroupMessage() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));
        when(dealGroupingService.formatTelegramPreview(group)).thenReturn("DEAL GROUP SIGNAL\nRSS_NEWS\nSEC_FILING");

        mockMvc.perform(get("/api/deal-groups/target-ticker:APGE/telegram-preview")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupKey").value("target-ticker:APGE"))
                .andExpect(jsonPath("$.messageText").value("DEAL GROUP SIGNAL\nRSS_NEWS\nSEC_FILING"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"));
    }

    @Test
    void sendTelegramWhenDisabledReturnsSafeResponse() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));

        mockMvc.perform(post("/api/deal-groups/target-ticker:APGE/send-telegram")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.telegramEnabled").value(false))
                .andExpect(jsonPath("$.telegramConfigured").value(false))
                .andExpect(jsonPath("$.message").value("Telegram is disabled; no external message will be sent."));

        verify(alertNotifier, never()).send(any());
    }

    @Test
    void sendTelegramWhenConfigMissingReturnsSafeResponse() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(telegramAlertSettings.enabled()).thenReturn(true);
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));

        mockMvc.perform(post("/api/deal-groups/target-ticker:APGE/send-telegram")
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
        DealGroupingService.DealGroupResponse group = group();
        when(telegramAlertSettings.enabled()).thenReturn(true);
        when(telegramAlertSettings.botToken()).thenReturn("token");
        when(telegramAlertSettings.chatId()).thenReturn("chat");
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));
        when(dealGroupingService.formatTelegramPreview(group)).thenReturn("DEAL GROUP SIGNAL");
        when(alertNotifier.send("DEAL GROUP SIGNAL"))
                .thenReturn(AlertNotifier.AlertNotificationResult.sent("SENT", "Telegram message sent."));

        mockMvc.perform(post("/api/deal-groups/target-ticker:APGE/send-telegram")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.telegramEnabled").value(true))
                .andExpect(jsonPath("$.telegramConfigured").value(true))
                .andExpect(jsonPath("$.message").value("Telegram message sent."));

        verify(alertNotifier).send("DEAL GROUP SIGNAL");
    }

    @Test
    void aiReviewLatestReturnsSafeResponse() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));
        when(dealGroupAiReviewService.latest("target-ticker:APGE"))
                .thenReturn(DealGroupAiReviewService.AiReviewResponse.empty(false, false, "No AI review has been saved for this deal group yet."));

        mockMvc.perform(get("/api/deal-groups/target-ticker:APGE/ai-review/latest")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openAiEnabled").value(false))
                .andExpect(jsonPath("$.message").value("No AI review has been saved for this deal group yet."));
    }

    @Test
    void aiReviewPostReturnsDisabledSafeResponse() throws Exception {
        DealGroupingService.DealGroupResponse group = group();
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));
        when(dealGroupAiReviewService.review("target-ticker:APGE"))
                .thenReturn(DealGroupAiReviewService.AiReviewResponse.empty(false, false, "OpenAI AI Review is disabled. Enable OPENAI_ANALYSIS_ENABLED=true to use it."));

        mockMvc.perform(post("/api/deal-groups/target-ticker:APGE/ai-review")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openAiEnabled").value(false))
                .andExpect(jsonPath("$.message").value("OpenAI AI Review is disabled. Enable OPENAI_ANALYSIS_ENABLED=true to use it."));
    }

    @Test
    void aiReviewEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/deal-groups/target-ticker:APGE/ai-review/latest"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/deal-groups/target-ticker:APGE/ai-review"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deal-groups/ai-review/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/deal-groups/ai-review/batch"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deal-groups/ai-review/batch-candidates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchAiReviewEndpointReturnsSafeResponse() throws Exception {
        when(dealGroupAiReviewService.batchReview(any()))
                .thenReturn(new DealGroupAiReviewService.BatchAiReviewResponse(
                        false,
                        false,
                        "OpenAI AI Review is disabled.",
                        10,
                        0,
                        0,
                        0,
                        List.of()
                ));

        mockMvc.perform(post("/api/deal-groups/ai-review/batch")
                        .with(httpBasic("tester", "secret"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "limit": 10,
                                  "skipAlreadyReviewed": true,
                                  "minPriority": "HIGH",
                                  "onlyPromising": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.message").value("OpenAI AI Review is disabled."));
    }

    @Test
    void batchAiReviewPreviewReturnsCandidatesWithoutOpenAiCall() throws Exception {
        when(dealGroupAiReviewService.previewBatchCandidates(any()))
                .thenReturn(new DealGroupAiReviewService.BatchCandidatePreviewResponse(
                        25,
                        1,
                        1,
                        List.of(
                                new DealGroupAiReviewService.BatchCandidatePreviewItem(
                                        "target-ticker:APGE",
                                        "AbbVie to Acquire Apogee",
                                        SignalInboxController.UnifiedPriority.HIGH,
                                        com.parsernews.model.DealRelevance.PUBLIC_CASH_ACQUISITION,
                                        com.parsernews.model.Tradability.HIGH,
                                        com.parsernews.model.DealTiming.EARLY,
                                        true,
                                        "Strict candidate.",
                                        null
                                ),
                                new DealGroupAiReviewService.BatchCandidatePreviewItem(
                                        "private-target",
                                        "Private target deal",
                                        SignalInboxController.UnifiedPriority.HIGH,
                                        com.parsernews.model.DealRelevance.PRIVATE_COMPANY_ACQUISITION,
                                        com.parsernews.model.Tradability.NOT_TRADABLE,
                                        com.parsernews.model.DealTiming.EARLY,
                                        false,
                                        null,
                                        "Tradability is not HIGH or MEDIUM."
                                )
                        )
                ));

        mockMvc.perform(get("/api/deal-groups/ai-review/batch-candidates?limit=25")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCount").value(1))
                .andExpect(jsonPath("$.candidates[0].included").value(true))
                .andExpect(jsonPath("$.candidates[0].groupKey").value("target-ticker:APGE"))
                .andExpect(jsonPath("$.candidates[1].included").value(false));
    }

    @Test
    void aiReviewSummaryEndpointReturnsCounts() throws Exception {
        when(dealGroupAiReviewService.summary())
                .thenReturn(new DealGroupAiReviewService.AiReviewSummaryResponse(
                        2,
                        3,
                        2,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        1,
                        1,
                        0,
                        List.of(new DealGroupAiReviewService.LatestAiReviewSummary(
                                "target-ticker:APGE",
                                "AbbVie to Acquire Apogee",
                                AiReviewVerdict.GOOD_SIGNAL,
                                AiReviewConfidence.HIGH,
                                Instant.parse("2026-06-20T12:00:00Z"),
                                "Useful signal."
                        ))
                ));

        mockMvc.perform(get("/api/deal-groups/ai-review/summary")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAiReviewed").value(2))
                .andExpect(jsonPath("$.totalAiReviewsSaved").value(3))
                .andExpect(jsonPath("$.uniqueGroupsReviewed").value(2))
                .andExpect(jsonPath("$.duplicateHistoricalReviewsIgnored").value(1))
                .andExpect(jsonPath("$.goodSignalCount").value(1))
                .andExpect(jsonPath("$.latestReviews[0].groupKey").value("target-ticker:APGE"));
    }

    @Test
    void exportCsvReturnsHeaderAndRows() throws Exception {
        when(dealGroupingService.groups(null, null, 500)).thenReturn(List.of(group()));

        mockMvc.perform(get("/api/deal-groups/export.csv")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv;charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("groupKey,title,buyerCompany,targetCompany")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("target-ticker:APGE")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("AbbVie to Acquire Apogee")));
    }

    @Test
    void statsCountReviewStatusesAndBreakdowns() throws Exception {
        when(dealGroupingService.groups(ManualReviewStatus.PENDING, null, 200))
                .thenReturn(List.of(groupWithReview("pending-group", ManualReviewStatus.PENDING, null)));
        when(dealGroupingService.groups(ManualReviewStatus.USEFUL, null, 200))
                .thenReturn(List.of(groupWithReview("useful-group", ManualReviewStatus.USEFUL, ManualReviewReason.GOOD_SIGNAL)));
        when(dealGroupingService.groups(ManualReviewStatus.IGNORED, null, 200))
                .thenReturn(List.of(
                        groupWithReview("ignored-private", ManualReviewStatus.IGNORED, ManualReviewReason.PRIVATE_COMPANY),
                        groupWithReview("ignored-false", ManualReviewStatus.IGNORED, ManualReviewReason.FALSE_POSITIVE)
                ));

        mockMvc.perform(get("/api/deal-groups/stats")
                        .with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGroups").value(4))
                .andExpect(jsonPath("$.pendingGroups").value(1))
                .andExpect(jsonPath("$.usefulGroups").value(1))
                .andExpect(jsonPath("$.ignoredGroups").value(2))
                .andExpect(jsonPath("$.highPriorityGroups").value(4))
                .andExpect(jsonPath("$.alertLikeGroups").value(4))
                .andExpect(jsonPath("$.groupedEvidenceTotal").value(8))
                .andExpect(jsonPath("$.averageEvidencePerGroup").value(2.0))
                .andExpect(jsonPath("$.reviewReasonBreakdown.GOOD_SIGNAL").value(1))
                .andExpect(jsonPath("$.reviewReasonBreakdown.PRIVATE_COMPANY").value(1))
                .andExpect(jsonPath("$.reviewReasonBreakdown.FALSE_POSITIVE").value(1))
                .andExpect(jsonPath("$.byDealRelevance.PUBLIC_CASH_ACQUISITION").value(4))
                .andExpect(jsonPath("$.byTradability.HIGH").value(4))
                .andExpect(jsonPath("$.byDealStage.DEFINITIVE_AGREEMENT").value(4))
                .andExpect(jsonPath("$.byDealTiming.EARLY").value(4))
                .andExpect(jsonPath("$.byPriority.HIGH").value(4));
    }

    @Test
    void sendAndExportRequireAuth() throws Exception {
        mockMvc.perform(post("/api/deal-groups/target-ticker:APGE/send-telegram"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deal-groups/export.csv"))
                .andExpect(status().isUnauthorized());
    }

    private DealGroupingService.DealGroupResponse group() {
        return groupWithReview("target-ticker:APGE", ManualReviewStatus.PENDING, ManualReviewReason.GOOD_SIGNAL);
    }

    private DealGroupingService.DealGroupResponse groupWithReview(
            String groupKey,
            ManualReviewStatus status,
            ManualReviewReason reason
    ) {
        return new DealGroupingService.DealGroupResponse(
                groupKey,
                SignalInboxController.SourceType.RSS_NEWS,
                1L,
                "AbbVie to Acquire Apogee",
                "AbbVie Inc.",
                "Apogee Therapeutics",
                "APGE",
                "1550760",
                "ABBV",
                "1551152",
                SignalInboxController.UnifiedPriority.HIGH,
                com.parsernews.model.DealRelevance.PUBLIC_CASH_ACQUISITION,
                com.parsernews.model.Tradability.HIGH,
                com.parsernews.model.DealStage.DEFINITIVE_AGREEMENT,
                com.parsernews.model.DealTiming.EARLY,
                status,
                reason,
                null,
                null,
                status != ManualReviewStatus.PENDING,
                List.of(
                        new DealGroupingService.RelatedSignalResponse(
                                SignalInboxController.SourceType.RSS_NEWS,
                                1L,
                                "AbbVie to Acquire Apogee",
                                "https://example.test/rss",
                                Instant.parse("2026-06-20T12:00:00Z"),
                                null,
                                "ACQUISITION",
                                SignalInboxController.UnifiedPriority.HIGH,
                                "primary RSS deal signal"
                        ),
                        new DealGroupingService.RelatedSignalResponse(
                                SignalInboxController.SourceType.SEC_FILING,
                                10L,
                                "APOGEE THERAPEUTICS INC 8-K",
                                "https://sec.gov/test",
                                Instant.parse("2026-06-20T12:01:00Z"),
                                java.time.LocalDate.of(2026, 6, 20),
                                "MERGER_AGREEMENT",
                                SignalInboxController.UnifiedPriority.HIGH,
                                "same target CIK"
                        )
                ),
                List.of("https://example.test/rss", "https://sec.gov/test"),
                List.of("RSS signal is alert eligible", "Multiple related signals found for this deal"),
                Instant.parse("2026-06-20T12:01:00Z")
        );
    }
}
