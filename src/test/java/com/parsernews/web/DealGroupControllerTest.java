package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.config.TelegramAlertSettings;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.service.AlertNotifier;
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
    void sendAndExportRequireAuth() throws Exception {
        mockMvc.perform(post("/api/deal-groups/target-ticker:APGE/send-telegram"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deal-groups/export.csv"))
                .andExpect(status().isUnauthorized());
    }

    private DealGroupingService.DealGroupResponse group() {
        return new DealGroupingService.DealGroupResponse(
                "target-ticker:APGE",
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
                ManualReviewStatus.PENDING,
                ManualReviewReason.GOOD_SIGNAL,
                null,
                null,
                false,
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
                List.of("Multiple related signals found for this deal"),
                Instant.parse("2026-06-20T12:01:00Z")
        );
    }
}
