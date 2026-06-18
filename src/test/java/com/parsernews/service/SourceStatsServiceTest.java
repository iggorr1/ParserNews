package com.parsernews.service;

import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventEntity;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.DetectedEventType;
import com.parsernews.persistence.ManualReviewStatus;
import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.NewsSourceEntity;
import com.parsernews.persistence.NewsSourceType;
import com.parsernews.persistence.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceStatsServiceTest {
    @Test
    void groupsArticleCandidateVerdictAndManualReviewCountsBySource() {
        NewsArticleEntity dealArticle = article(
                "GlobeNewswire M&A",
                "Target Enters Definitive Merger Agreement",
                "Shareholders will receive $5.00 per share in cash.",
                "https://www.globenewswire.com/news/deal"
        );
        NewsArticleEntity lawFirmArticle = article(
                "GlobeNewswire M&A",
                "Shareholder Alert: Law Firm Investigates Proposed Deal",
                "The law firm announces an investigation and class action.",
                "https://www.globenewswire.com/news/law"
        );
        NewsArticleEntity regularArticle = article(
                "TMX Newsfile Last 25 Stories",
                "Regular Company Update",
                "No deal here.",
                "https://www.newsfilecorp.com/release/1"
        );
        DetectedEventEntity dealEvent = event(dealArticle, CandidateStrength.HIGH, ReviewStatus.HIGH_PRIORITY_SIGNAL,
                "definitive agreement|per share in cash|shareholders will receive");
        dealEvent.updateManualReview(ManualReviewStatus.USEFUL, "Good");
        DetectedEventEntity lawFirmEvent = event(lawFirmArticle, CandidateStrength.MEDIUM, ReviewStatus.MANUAL_REVIEW,
                "merger agreement");
        lawFirmEvent.updateManualReview(ManualReviewStatus.IGNORED, "Law firm");

        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(articleRepository.findAll()).thenReturn(List.of(dealArticle, lawFirmArticle, regularArticle));
        when(eventRepository.findAll()).thenReturn(List.of(dealEvent, lawFirmEvent));
        SourceStatsService service = new SourceStatsService(
                articleRepository,
                eventRepository,
                new CandidateReviewInsightService()
        );

        List<SourceStatsService.SourceStatsResponse> response = service.sourceStats();

        SourceStatsService.SourceStatsResponse globe = response.stream()
                .filter(item -> item.source().equals("GlobeNewswire M&A"))
                .findFirst()
                .orElseThrow();
        assertThat(globe.host()).isEqualTo("www.globenewswire.com");
        assertThat(globe.totalArticles()).isEqualTo(2);
        assertThat(globe.totalCandidates()).isEqualTo(2);
        assertThat(globe.highCandidates()).isEqualTo(1);
        assertThat(globe.mediumCandidates()).isEqualTo(1);
        assertThat(globe.likelyDeals()).isEqualTo(1);
        assertThat(globe.lawFirmAlerts()).isEqualTo(1);
        assertThat(globe.manualUseful()).isEqualTo(1);
        assertThat(globe.manualIgnored()).isEqualTo(1);

        SourceStatsService.SourceStatsResponse newsfile = response.stream()
                .filter(item -> item.source().equals("TMX Newsfile Last 25 Stories"))
                .findFirst()
                .orElseThrow();
        assertThat(newsfile.totalArticles()).isEqualTo(1);
        assertThat(newsfile.totalCandidates()).isZero();
    }

    private NewsArticleEntity article(String sourceName, String headline, String body, String url) {
        return new NewsArticleEntity(
                new NewsSourceEntity(sourceName, NewsSourceType.RSS, "https://example.com/feed"),
                "hash-" + url.hashCode(),
                "TEST",
                "Test Company",
                headline,
                body,
                url,
                Instant.parse("2026-06-18T10:00:00Z")
        );
    }

    private DetectedEventEntity event(
            NewsArticleEntity article,
            CandidateStrength strength,
            ReviewStatus reviewStatus,
            String positives
    ) {
        return new DetectedEventEntity(
                article,
                DetectedEventType.DEFINITIVE_AGREEMENT,
                reviewStatus,
                90,
                "Test Company",
                article.getTicker(),
                "Buyer LLC",
                "$5.00",
                "CASH",
                "40%",
                strength == CandidateStrength.HIGH ? 90 : 60,
                strength,
                "Matched candidate signal.",
                strength == CandidateStrength.HIGH,
                "Review test",
                positives,
                "",
                "",
                "Test explanation"
        );
    }
}
