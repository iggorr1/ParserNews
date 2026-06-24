package com.parsernews.service;

import com.parsernews.config.OpenAiAnalysisSettings;
import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealStage;
import com.parsernews.model.DealTiming;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.DealGroupAiReviewEntity;
import com.parsernews.persistence.DealGroupAiReviewRepository;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.web.SignalInboxController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class DealGroupAiReviewServiceTest {
    private final OpenAiAnalysisClient client = mock(OpenAiAnalysisClient.class);
    private final DealGroupingService dealGroupingService = mock(DealGroupingService.class);
    private final DealGroupAiReviewRepository repository = mock(DealGroupAiReviewRepository.class);

    @Test
    void disabledModeReturnsSafeResponseWithoutApiCall() {
        DealGroupAiReviewService service = service(false, "");

        DealGroupAiReviewService.AiReviewResponse response = service.review("target-ticker:APGE");

        assertThat(response.openAiEnabled()).isFalse();
        assertThat(response.message()).contains("disabled");
        verify(client, never()).reviewDealGroup(any(), any(), any(), any());
    }

    @Test
    void missingApiKeyReturnsSafeResponseWithoutApiCall() {
        DealGroupAiReviewService service = service(true, "");

        DealGroupAiReviewService.AiReviewResponse response = service.review("target-ticker:APGE");

        assertThat(response.openAiEnabled()).isTrue();
        assertThat(response.openAiConfigured()).isFalse();
        assertThat(response.message()).contains("OPENAI_API_KEY is missing");
        verify(client, never()).reviewDealGroup(any(), any(), any(), any());
    }

    @Test
    void mockOpenAiGoodSignalForAbbVieApogeeIsSaved() {
        DealGroupingService.DealGroupResponse group = group(
                "target-ticker:APGE",
                "AbbVie to Acquire Apogee",
                "AbbVie Inc.",
                "Apogee Therapeutics",
                "ABBV",
                "APGE",
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH
        );
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));
        when(client.reviewDealGroup(any(), any(), any(), any())).thenReturn(new OpenAiAnalysisClient.AiReviewResult(
                AiReviewVerdict.GOOD_SIGNAL,
                AiReviewConfidence.HIGH,
                true,
                ManualReviewStatus.USEFUL,
                ManualReviewReason.GOOD_SIGNAL,
                "Public target with cash acquisition evidence.",
                List.of("verify offer terms"),
                "{\"verdict\":\"GOOD_SIGNAL\"}"
        ));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DealGroupAiReviewService service = service(true, "test-key");

        DealGroupAiReviewService.AiReviewResponse response = service.review("target-ticker:APGE");

        assertThat(response.verdict()).isEqualTo(AiReviewVerdict.GOOD_SIGNAL);
        assertThat(response.confidence()).isEqualTo(AiReviewConfidence.HIGH);
        assertThat(response.tradablePublicTarget()).isTrue();
        assertThat(response.suggestedReviewStatus()).isEqualTo(ManualReviewStatus.USEFUL);
        ArgumentCaptor<DealGroupAiReviewEntity> captor = ArgumentCaptor.forClass(DealGroupAiReviewEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGroupKey()).isEqualTo("target-ticker:APGE");
        assertThat(captor.getValue().getRiskFlags()).contains("verify offer terms");
    }

    @Test
    void mockOpenAiNotTradableForMdaBlueCanyonIsSaved() {
        DealGroupingService.DealGroupResponse group = group(
                "names:mda-space:blue-canyon-technologies",
                "MDA Space announces definitive agreement to acquire US-based Blue Canyon Technologies LLC",
                "MDA Space",
                "Blue Canyon Technologies LLC",
                "MDA",
                null,
                DealRelevance.PRIVATE_COMPANY_ACQUISITION,
                Tradability.NOT_TRADABLE
        );
        when(dealGroupingService.group(group.groupKey())).thenReturn(Optional.of(group));
        when(client.reviewDealGroup(any(), any(), any(), any())).thenReturn(new OpenAiAnalysisClient.AiReviewResult(
                AiReviewVerdict.NOT_TRADABLE,
                AiReviewConfidence.HIGH,
                false,
                ManualReviewStatus.IGNORED,
                ManualReviewReason.PRIVATE_COMPANY,
                "Target appears private/LLC and not directly tradable.",
                List.of("private company target", "no public target ticker"),
                "{\"verdict\":\"NOT_TRADABLE\"}"
        ));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DealGroupAiReviewService service = service(true, "test-key");

        DealGroupAiReviewService.AiReviewResponse response = service.review(group.groupKey());

        assertThat(response.verdict()).isEqualTo(AiReviewVerdict.NOT_TRADABLE);
        assertThat(response.tradablePublicTarget()).isFalse();
        assertThat(response.suggestedReviewReason()).isEqualTo(ManualReviewReason.PRIVATE_COMPANY);
    }

    @Test
    void reviewUsesRuntimeKeyWhenConfiguredFromUi() {
        DealGroupingService.DealGroupResponse group = group(
                "target-ticker:APGE",
                "AbbVie to Acquire Apogee",
                "AbbVie Inc.",
                "Apogee Therapeutics",
                "ABBV",
                "APGE",
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH
        );
        when(dealGroupingService.group("target-ticker:APGE")).thenReturn(Optional.of(group));
        when(client.reviewDealGroup(any(), any(), any(), any())).thenReturn(new OpenAiAnalysisClient.AiReviewResult(
                AiReviewVerdict.GOOD_SIGNAL,
                AiReviewConfidence.HIGH,
                true,
                ManualReviewStatus.USEFUL,
                ManualReviewReason.GOOD_SIGNAL,
                "Runtime key was used.",
                List.of(),
                "{}"
        ));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OpenAiRuntimeSettingsService runtimeSettings = new OpenAiRuntimeSettingsService(
                new OpenAiAnalysisSettings(false, "", "gpt-4.1-mini", 12000)
        );
        runtimeSettings.update(true, "test-runtime-key-1234abcd", "gpt-4.1-mini", 12000);
        DealGroupAiReviewService service = new DealGroupAiReviewService(
                runtimeSettings,
                client,
                dealGroupingService,
                repository
        );

        DealGroupAiReviewService.AiReviewResponse response = service.review("target-ticker:APGE");

        assertThat(response.verdict()).isEqualTo(AiReviewVerdict.GOOD_SIGNAL);
        verify(client).reviewDealGroup(eq("gpt-4.1-mini"), eq("test-runtime-key-1234abcd"), any(), any());
    }

    @Test
    void latestReturnsSavedReview() {
        DealGroupAiReviewEntity entity = new DealGroupAiReviewEntity(
                "target-ticker:APGE",
                "gpt-4.1-mini",
                AiReviewVerdict.GOOD_SIGNAL,
                AiReviewConfidence.HIGH,
                true,
                ManualReviewStatus.USEFUL,
                ManualReviewReason.GOOD_SIGNAL,
                "Looks useful.",
                "verify offer",
                "{}"
        );
        when(repository.findTopByGroupKeyOrderByCreatedAtDesc("target-ticker:APGE")).thenReturn(Optional.of(entity));

        DealGroupAiReviewService.AiReviewResponse response = service(true, "test-key").latest("target-ticker:APGE");

        assertThat(response.verdict()).isEqualTo(AiReviewVerdict.GOOD_SIGNAL);
        assertThat(response.riskFlags()).containsExactly("verify offer");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void batchDisabledModeReturnsSafeResponseWithoutApiCall() {
        when(dealGroupingService.groups(null, null, 200)).thenReturn(List.of(group(
                "target-ticker:APGE",
                "AbbVie to Acquire Apogee",
                "AbbVie Inc.",
                "Apogee Therapeutics",
                "ABBV",
                "APGE",
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH
        )));
        DealGroupAiReviewService service = service(false, "");

        DealGroupAiReviewService.BatchAiReviewResponse response = service.batchReview(null);

        assertThat(response.enabled()).isFalse();
        assertThat(response.message()).contains("disabled");
        assertThat(response.reviewedCount()).isZero();
        assertThat(response.skippedCount()).isEqualTo(1);
        verify(client, never()).reviewDealGroup(any(), any(), any(), any());
    }

    @Test
    void batchMissingApiKeyReturnsSafeResponseWithoutApiCall() {
        when(dealGroupingService.groups(null, null, 200)).thenReturn(List.of(group(
                "target-ticker:APGE",
                "AbbVie to Acquire Apogee",
                "AbbVie Inc.",
                "Apogee Therapeutics",
                "ABBV",
                "APGE",
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH
        )));
        DealGroupAiReviewService service = service(true, "");

        DealGroupAiReviewService.BatchAiReviewResponse response = service.batchReview(null);

        assertThat(response.enabled()).isTrue();
        assertThat(response.configured()).isFalse();
        assertThat(response.message()).contains("OPENAI_API_KEY is missing");
        verify(client, never()).reviewDealGroup(any(), any(), any(), any());
    }

    @Test
    void batchSelectionExcludesPrivateLawNoiseAndNotTradableGroups() {
        DealGroupingService.DealGroupResponse good = group(
                "target-ticker:APGE",
                "AbbVie to Acquire Apogee",
                "AbbVie Inc.",
                "Apogee Therapeutics",
                "ABBV",
                "APGE",
                DealRelevance.PUBLIC_CASH_ACQUISITION,
                Tradability.HIGH
        );
        when(dealGroupingService.groups(null, null, 200)).thenReturn(List.of(
                good,
                groupWithSignals("private-target", DealRelevance.PRIVATE_COMPANY_ACQUISITION, Tradability.NOT_TRADABLE, DealStage.DEFINITIVE_AGREEMENT, DealTiming.EARLY, ManualReviewStatus.PENDING, null, List.of("private company target")),
                groupWithSignals("law-alert", DealRelevance.LAW_FIRM_OR_SHAREHOLDER_ALERT, Tradability.NOT_TRADABLE, DealStage.LITIGATION_OR_LAW_FIRM_UPDATE, DealTiming.NOISE, ManualReviewStatus.PENDING, null, List.of("law firm alert")),
                groupWithSignals("late-stage", DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH, DealStage.SHAREHOLDER_APPROVAL, DealTiming.LATE_STAGE, ManualReviewStatus.PENDING, ManualReviewReason.LATE_STAGE_UPDATE, List.of("not initial announcement")),
                groupWithSignals("reverse-takeover", DealRelevance.REVERSE_TAKEOVER, Tradability.NOT_TRADABLE, DealStage.INITIAL_ANNOUNCEMENT, DealTiming.EARLY, ManualReviewStatus.PENDING, null, List.of("reverse takeover"))
        ));
        when(client.reviewDealGroup(any(), any(), any(), any())).thenReturn(goodSignal());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DealGroupAiReviewService service = service(true, "test-key");

        DealGroupAiReviewService.BatchAiReviewResponse response = service.batchReview(new DealGroupAiReviewService.BatchAiReviewRequest(10, true, SignalInboxController.UnifiedPriority.HIGH, true));

        assertThat(response.reviewedCount()).isEqualTo(1);
        assertThat(response.results()).extracting(DealGroupAiReviewService.BatchAiReviewResult::groupKey)
                .containsExactly("target-ticker:APGE");
        verify(client, times(1)).reviewDealGroup(any(), any(), any(), any());
    }

    @Test
    void batchRespectsLimitAndSkipsAlreadyReviewedGroups() {
        when(dealGroupingService.groups(null, null, 200)).thenReturn(List.of(
                groupWithSignals("already-reviewed", DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH, DealStage.DEFINITIVE_AGREEMENT, DealTiming.EARLY, ManualReviewStatus.PENDING, null, List.of()),
                groupWithSignals("group-1", DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH, DealStage.DEFINITIVE_AGREEMENT, DealTiming.EARLY, ManualReviewStatus.PENDING, null, List.of()),
                groupWithSignals("group-2", DealRelevance.PUBLIC_TAKE_PRIVATE, Tradability.HIGH, DealStage.INITIAL_ANNOUNCEMENT, DealTiming.EARLY, ManualReviewStatus.PENDING, null, List.of())
        ));
        when(repository.existsByGroupKey("already-reviewed")).thenReturn(true);
        when(client.reviewDealGroup(any(), any(), any(), any())).thenReturn(goodSignal());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DealGroupAiReviewService service = service(true, "test-key");

        DealGroupAiReviewService.BatchAiReviewResponse response = service.batchReview(new DealGroupAiReviewService.BatchAiReviewRequest(1, true, SignalInboxController.UnifiedPriority.HIGH, true));

        assertThat(response.requestedLimit()).isEqualTo(1);
        assertThat(response.reviewedCount()).isEqualTo(1);
        assertThat(response.results()).extracting(DealGroupAiReviewService.BatchAiReviewResult::groupKey)
                .containsExactly("group-1");
    }

    @Test
    void summaryReturnsVerdictAndConfidenceCounts() {
        DealGroupAiReviewEntity good = new DealGroupAiReviewEntity(
                "good-group",
                "gpt-4.1-mini",
                AiReviewVerdict.GOOD_SIGNAL,
                AiReviewConfidence.HIGH,
                true,
                ManualReviewStatus.USEFUL,
                ManualReviewReason.GOOD_SIGNAL,
                "Good public target signal.",
                "",
                "{}"
        );
        DealGroupAiReviewEntity notTradable = new DealGroupAiReviewEntity(
                "private-group",
                "gpt-4.1-mini",
                AiReviewVerdict.NOT_TRADABLE,
                AiReviewConfidence.MEDIUM,
                false,
                ManualReviewStatus.IGNORED,
                ManualReviewReason.PRIVATE_COMPANY,
                "Private target.",
                "",
                "{}"
        );
        when(repository.findAll()).thenReturn(List.of(good, notTradable));
        when(repository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(good, notTradable));
        when(dealGroupingService.groups(null, null, 200)).thenReturn(List.of(
                groupWithSignals("good-group", DealRelevance.PUBLIC_CASH_ACQUISITION, Tradability.HIGH, DealStage.DEFINITIVE_AGREEMENT, DealTiming.EARLY, ManualReviewStatus.PENDING, null, List.of()),
                groupWithSignals("private-group", DealRelevance.PRIVATE_COMPANY_ACQUISITION, Tradability.NOT_TRADABLE, DealStage.DEFINITIVE_AGREEMENT, DealTiming.EARLY, ManualReviewStatus.PENDING, null, List.of())
        ));

        DealGroupAiReviewService.AiReviewSummaryResponse response = service(true, "test-key").summary();

        assertThat(response.totalAiReviewed()).isEqualTo(2);
        assertThat(response.goodSignalCount()).isEqualTo(1);
        assertThat(response.notTradableCount()).isEqualTo(1);
        assertThat(response.highConfidenceCount()).isEqualTo(1);
        assertThat(response.mediumConfidenceCount()).isEqualTo(1);
        assertThat(response.latestReviews()).hasSize(2);
    }

    private DealGroupAiReviewService service(boolean enabled, String apiKey) {
        return new DealGroupAiReviewService(
                new OpenAiRuntimeSettingsService(new OpenAiAnalysisSettings(enabled, apiKey, "gpt-4.1-mini", 12000)),
                client,
                dealGroupingService,
                repository
        );
    }

    private OpenAiAnalysisClient.AiReviewResult goodSignal() {
        return new OpenAiAnalysisClient.AiReviewResult(
                AiReviewVerdict.GOOD_SIGNAL,
                AiReviewConfidence.HIGH,
                true,
                ManualReviewStatus.USEFUL,
                ManualReviewReason.GOOD_SIGNAL,
                "Public target with useful deal evidence.",
                List.of(),
                "{\"verdict\":\"GOOD_SIGNAL\"}"
        );
    }

    private DealGroupingService.DealGroupResponse groupWithSignals(
            String groupKey,
            DealRelevance relevance,
            Tradability tradability,
            DealStage stage,
            DealTiming timing,
            ManualReviewStatus status,
            ManualReviewReason reason,
            List<String> warnings
    ) {
        return new DealGroupingService.DealGroupResponse(
                groupKey,
                SignalInboxController.SourceType.RSS_NEWS,
                1L,
                "Test Deal Group " + groupKey,
                "Buyer Corp.",
                "Target Corp.",
                relevance == DealRelevance.PRIVATE_COMPANY_ACQUISITION ? null : "TGT",
                relevance == DealRelevance.PRIVATE_COMPANY_ACQUISITION ? null : "123456",
                "BUY",
                "654321",
                SignalInboxController.UnifiedPriority.HIGH,
                relevance,
                tradability,
                stage,
                timing,
                status,
                reason,
                null,
                null,
                false,
                List.of(new DealGroupingService.RelatedSignalResponse(
                        SignalInboxController.SourceType.RSS_NEWS,
                        1L,
                        "Test Deal Group " + groupKey,
                        "https://example.test/" + groupKey,
                        Instant.parse("2026-06-20T12:00:00Z"),
                        null,
                        "ACQUISITION",
                        SignalInboxController.UnifiedPriority.HIGH,
                        "test signal"
                )),
                List.of("https://example.test/" + groupKey),
                warnings,
                Instant.parse("2026-06-20T12:00:00Z")
        );
    }

    private DealGroupingService.DealGroupResponse group(
            String groupKey,
            String title,
            String buyerCompany,
            String targetCompany,
            String buyerTicker,
            String targetTicker,
            DealRelevance relevance,
            Tradability tradability
    ) {
        return new DealGroupingService.DealGroupResponse(
                groupKey,
                SignalInboxController.SourceType.RSS_NEWS,
                1L,
                title,
                buyerCompany,
                targetCompany,
                targetTicker,
                targetTicker == null ? null : "123456",
                buyerTicker,
                buyerTicker == null ? null : "654321",
                SignalInboxController.UnifiedPriority.HIGH,
                relevance,
                tradability,
                DealStage.DEFINITIVE_AGREEMENT,
                DealTiming.EARLY,
                ManualReviewStatus.PENDING,
                null,
                null,
                null,
                false,
                List.of(new DealGroupingService.RelatedSignalResponse(
                        SignalInboxController.SourceType.RSS_NEWS,
                        1L,
                        title,
                        "https://example.test/news",
                        Instant.parse("2026-06-20T12:00:00Z"),
                        null,
                        "ACQUISITION",
                        SignalInboxController.UnifiedPriority.HIGH,
                        "primary RSS deal signal"
                )),
                List.of("https://example.test/news"),
                List.of(),
                Instant.parse("2026-06-20T12:00:00Z")
        );
    }
}
