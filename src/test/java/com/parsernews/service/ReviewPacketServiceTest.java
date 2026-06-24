package com.parsernews.service;

import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.persistence.ScanRunStatus;
import com.parsernews.persistence.ScanRunTriggerType;
import com.parsernews.web.SignalInboxController;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewPacketServiceTest {
    @Test
    void markdownContainsReviewSectionsAndMasksOpenAiKey() {
        StatusService statusService = mock(StatusService.class);
        SchedulerStatusService schedulerStatusService = mock(SchedulerStatusService.class);
        OpenAiRuntimeSettingsService openAiRuntimeSettingsService = mock(OpenAiRuntimeSettingsService.class);
        TelegramRuntimeSettingsService telegramRuntimeSettingsService = mock(TelegramRuntimeSettingsService.class);
        DealGroupingService dealGroupingService = mock(DealGroupingService.class);
        DealGroupAiReviewService dealGroupAiReviewService = mock(DealGroupAiReviewService.class);
        ScanRunRepository scanRunRepository = mock(ScanRunRepository.class);

        when(statusService.status()).thenReturn(status());
        when(schedulerStatusService.status()).thenReturn(schedulerStatus());
        when(openAiRuntimeSettingsService.effectiveSettings()).thenReturn(new OpenAiRuntimeSettingsService.EffectiveOpenAiSettings(
                true,
                true,
                "raw-openai-secret-value-123456",
                "gpt-4.1-mini",
                12000,
                OpenAiRuntimeSettingsService.KeySource.RUNTIME,
                "****3456",
                "OpenAI AI Review is enabled using runtime key."
        ));
        when(telegramRuntimeSettingsService.effectiveSettings()).thenReturn(new TelegramRuntimeSettingsService.EffectiveTelegramSettings(
                true,
                true,
                "123456:raw-telegram-token",
                "987654321",
                TelegramRuntimeSettingsService.SecretSource.RUNTIME,
                TelegramRuntimeSettingsService.SecretSource.RUNTIME,
                "123...oken",
                "987...4321",
                "Telegram is enabled using runtime token and runtime chat id."
        ));
        when(dealGroupingService.groups(null, null, 30)).thenReturn(List.of(group()));
        when(dealGroupingService.groups(null, null, 200)).thenReturn(List.of(group()));
        when(dealGroupAiReviewService.summary()).thenReturn(aiSummary());
        when(dealGroupAiReviewService.previewBatchCandidates(org.mockito.ArgumentMatchers.any()))
                .thenReturn(batchPreview());
        when(scanRunRepository.findTop100ByOrderByStartedAtDesc()).thenReturn(List.of());

        ReviewPacketService service = new ReviewPacketService(
                statusService,
                schedulerStatusService,
                openAiRuntimeSettingsService,
                telegramRuntimeSettingsService,
                dealGroupingService,
                dealGroupAiReviewService,
                scanRunRepository
        );

        String markdown = service.markdown();

        assertThat(markdown)
                .contains("# ParserNews Review Packet")
                .contains("## App Status")
                .contains("## Scheduler Status")
                .contains("## OpenAI Status")
                .contains("## Telegram Status")
                .contains("## Deal Group Quality Stats")
                .contains("## AI Review Summary")
                .contains("## Batch AI Candidates Preview")
                .contains("## Top Deal Groups")
                .contains("AbbVie to Acquire Apogee")
                .contains("****3456")
                .contains("123...oken")
                .contains("987...4321")
                .doesNotContain("raw-openai-secret-value-123456")
                .doesNotContain("123456:raw-telegram-token")
                .doesNotContain("987654321");
    }

    private StatusService.StatusResponse status() {
        return new StatusService.StatusResponse(
                StatusService.HealthStatus.OK,
                new StatusService.ConfigSummary(true, false, false, false),
                new StatusService.ScanRunSummary(
                        1L,
                        ScanRunStatus.SUCCESS,
                        Instant.parse("2026-06-20T12:00:00Z"),
                        Instant.parse("2026-06-20T12:01:00Z"),
                        ScanRunTriggerType.MANUAL,
                        10,
                        2,
                        1
                ),
                new StatusService.ArticleEventStats(20, 5, 2, 2, 1),
                new StatusService.AlertStats(1, 0)
        );
    }

    private SchedulerStatusService.SchedulerStatusResponse schedulerStatus() {
        return new SchedulerStatusService.SchedulerStatusResponse(
                true,
                null,
                false,
                false,
                null,
                null,
                null,
                List.of(),
                null,
                "SCHEDULED_FULL_REFRESH",
                900000,
                null,
                false,
                false,
                "Full Refresh runs only when you click the button."
        );
    }

    private DealGroupAiReviewService.AiReviewSummaryResponse aiSummary() {
        return new DealGroupAiReviewService.AiReviewSummaryResponse(
                1,
                1,
                1,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                1,
                0,
                0,
                List.of(new DealGroupAiReviewService.LatestAiReviewSummary(
                        "target-ticker:APGE",
                        "AbbVie to Acquire Apogee",
                        AiReviewVerdict.GOOD_SIGNAL,
                        AiReviewConfidence.HIGH,
                        Instant.parse("2026-06-20T12:05:00Z"),
                        "Public cash acquisition signal."
                ))
        );
    }

    private DealGroupAiReviewService.BatchCandidatePreviewResponse batchPreview() {
        return new DealGroupAiReviewService.BatchCandidatePreviewResponse(
                25,
                1,
                0,
                List.of(new DealGroupAiReviewService.BatchCandidatePreviewItem(
                        "target-ticker:APGE",
                        "AbbVie to Acquire Apogee",
                        SignalInboxController.UnifiedPriority.HIGH,
                        DealRelevance.PUBLIC_CASH_ACQUISITION,
                        Tradability.HIGH,
                        DealTiming.EARLY,
                        true,
                        "Strict public cash deal candidate.",
                        null
                ))
        );
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
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH,
                DealStage.DEFINITIVE_AGREEMENT,
                DealTiming.EARLY,
                ManualReviewStatus.PENDING,
                ManualReviewReason.GOOD_SIGNAL,
                null,
                null,
                false,
                List.of(new DealGroupingService.RelatedSignalResponse(
                        SignalInboxController.SourceType.RSS_NEWS,
                        1L,
                        "AbbVie to Acquire Apogee",
                        "https://example.test/apge",
                        Instant.parse("2026-06-20T12:00:00Z"),
                        null,
                        "ACQUISITION",
                        SignalInboxController.UnifiedPriority.HIGH,
                        "same target ticker"
                )),
                List.of("https://example.test/apge"),
                List.of("RSS signal is alert eligible"),
                Instant.parse("2026-06-20T12:00:00Z")
        );
    }
}
