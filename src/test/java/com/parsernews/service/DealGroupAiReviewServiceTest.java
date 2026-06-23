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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private DealGroupAiReviewService service(boolean enabled, String apiKey) {
        return new DealGroupAiReviewService(
                new OpenAiAnalysisSettings(enabled, apiKey, "gpt-4.1-mini", 12000),
                client,
                dealGroupingService,
                repository
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
