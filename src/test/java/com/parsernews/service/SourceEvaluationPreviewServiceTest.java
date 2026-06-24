package com.parsernews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.RulesConfigLoader;
import com.parsernews.model.DealRelevance;
import com.parsernews.model.DealTiming;
import com.parsernews.model.Tradability;
import com.parsernews.persistence.CandidateStrength;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvaluationPreviewServiceTest {
    @Test
    void validRssPreviewReturnsItemsAndKeepsStrictCandidate() throws Exception {
        SourceEvaluationPreviewService service = service(rss("""
                <item>
                    <title>AbbVie Announces Definitive Agreement to Acquire Apogee Therapeutics for $3.50 Per Share in Cash</title>
                    <link>https://example.com/abbvie-apogee</link>
                    <pubDate>Wed, 24 Jun 2026 10:00:00 GMT</pubDate>
                    <description>Apogee Therapeutics (NASDAQ: APGE) will be acquired by AbbVie in an all-cash transaction. Shareholders will receive $3.50 per share in cash.</description>
                </item>
                """));

        SourceEvaluationPreviewService.SourceEvaluationPreviewResponse response = service.preview(
                new SourceEvaluationPreviewService.SourceEvaluationPreviewRequest("Test source", "https://example.com/rss.xml", 50)
        );

        assertThat(response.fetchedCount()).isEqualTo(1);
        assertThat(response.candidateCount()).isEqualTo(1);
        assertThat(response.strictCandidateCount()).isEqualTo(1);
        assertThat(response.recommendation()).isEqualTo(SourceEvaluationPreviewService.Recommendation.KEEP);
        assertThat(response.previewItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.priority()).isEqualTo(CandidateStrength.HIGH);
                    assertThat(item.dealRelevance()).isEqualTo(DealRelevance.PUBLIC_CASH_ACQUISITION);
                    assertThat(item.tradability()).isEqualTo(Tradability.HIGH);
                    assertThat(item.dealTiming()).isEqualTo(DealTiming.EARLY);
                    assertThat(item.alertEligible()).isTrue();
                });
    }

    @Test
    void invalidUrlIsRejectedBeforeNetworkCall() throws Exception {
        SourceEvaluationPreviewService service = service(rss(""));

        assertThatThrownBy(() -> service.preview(new SourceEvaluationPreviewService.SourceEvaluationPreviewRequest(
                "Bad source",
                "http://example.com/rss.xml",
                50
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void privatePlacementOfferingIsClassifiedAsNotTradableNoise() throws Exception {
        SourceEvaluationPreviewService service = service(rss("""
                <item>
                    <title>GD Culture Announces Registered Direct Offering and Private Placement</title>
                    <link>https://example.com/offering</link>
                    <description>The company announced a registered direct offering and concurrent private placement.</description>
                </item>
                """));

        SourceEvaluationPreviewService.SourceEvaluationPreviewResponse response = service.preview(
                new SourceEvaluationPreviewService.SourceEvaluationPreviewRequest("Test source", "https://example.com/rss.xml", 50)
        );

        assertThat(response.strictCandidateCount()).isZero();
        assertThat(response.noiseCount()).isEqualTo(1);
        assertThat(response.previewItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.priority()).isEqualTo(CandidateStrength.NONE);
                    assertThat(item.dealRelevance()).isEqualTo(DealRelevance.NOT_TRADABLE);
                    assertThat(item.tradability()).isEqualTo(Tradability.NOT_TRADABLE);
                    assertThat(item.alertEligible()).isFalse();
                });
    }

    @Test
    void nonCandidateFeedWithManyItemsIsRecommendedDisabled() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < 11; index++) {
            items.append("""
                    <item>
                        <title>Company Reports Quarterly Product Update %d</title>
                        <link>https://example.com/noise-%d</link>
                        <description>Routine operating update with no M&amp;A signal.</description>
                    </item>
                    """.formatted(index, index));
        }
        SourceEvaluationPreviewService service = service(rss(items.toString()));

        SourceEvaluationPreviewService.SourceEvaluationPreviewResponse response = service.preview(
                new SourceEvaluationPreviewService.SourceEvaluationPreviewRequest("Noisy source", "https://example.com/rss.xml", 50)
        );

        assertThat(response.fetchedCount()).isEqualTo(11);
        assertThat(response.candidateCount()).isZero();
        assertThat(response.recommendation()).isEqualTo(SourceEvaluationPreviewService.Recommendation.DISABLE);
    }

    @Test
    void previewDoesNotPersistArticleEvents() throws Exception {
        SourceEvaluationPreviewService service = service(rss("""
                <item>
                    <title>Company to be acquired by Buyer for $5.00 per share in cash</title>
                    <link>https://example.com/deal</link>
                    <description>Shareholders will receive $5.00 per share in cash.</description>
                </item>
                """));

        SourceEvaluationPreviewService.SourceEvaluationPreviewResponse response = service.preview(
                new SourceEvaluationPreviewService.SourceEvaluationPreviewRequest("Preview only", "https://example.com/rss.xml", 50)
        );

        assertThat(response.fetchedCount()).isEqualTo(1);
        assertThat(SourceEvaluationPreviewService.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("Repository"));
    }

    private SourceEvaluationPreviewService service(String rssXml) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(rssXml);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return new SourceEvaluationPreviewService(
                new RuleBasedNewsAnalyzer(new RulesConfigLoader(new ObjectMapper())),
                new CandidateScoringService(),
                new CandidateReviewInsightService(),
                new DealTermsExtractionService(),
                new DealRelevanceService(),
                new DealStageDetectionService(),
                new AlertEligibilityService(),
                new FalsePositiveFilter(),
                httpClient
        );
    }

    private String rss(String items) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Test RSS</title>
                    %s
                  </channel>
                </rss>
                """.formatted(items);
    }
}
