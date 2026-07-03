package com.parsernews.service;

import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LatencyServiceTest {

    // Build a fully-stubbed article mock up front. Stubbing must complete before the mock is
    // used as an argument to another when()/thenReturn(), otherwise Mockito reports
    // UnfinishedStubbingException.
    private static NewsArticleEntity article(Instant published, Instant collected) {
        NewsArticleEntity e = mock(NewsArticleEntity.class);
        when(e.getPublishedAt()).thenReturn(published);
        when(e.getCollectedAt()).thenReturn(collected);
        return e;
    }

    private static LatencyService serviceReturning(List<NewsArticleEntity> articles) {
        NewsArticleRepository repo = mock(NewsArticleRepository.class);
        when(repo.findTop200ByOrderByCollectedAtDesc()).thenReturn(articles);
        return new LatencyService(repo);
    }

    @Test
    void computesMedianAndPercentilesFromDeltas() {
        Instant base = Instant.parse("2026-07-03T10:00:00Z");
        // deltas: 10s, 30s, 60s, 120s, 300s
        List<NewsArticleEntity> articles = List.of(
                article(base, base.plusSeconds(10)),
                article(base, base.plusSeconds(30)),
                article(base, base.plusSeconds(60)),
                article(base, base.plusSeconds(120)),
                article(base, base.plusSeconds(300))
        );

        LatencyService.LatencyStats stats = serviceReturning(articles).detectionLatency();

        assertThat(stats.sampleSize()).isEqualTo(5);
        assertThat(stats.fastestSeconds()).isEqualTo(10);
        assertThat(stats.slowestSeconds()).isEqualTo(300);
        assertThat(stats.medianSeconds()).isEqualTo(60);   // nearest-rank p50 of 5 -> index 2
        assertThat(stats.p90Seconds()).isEqualTo(300);     // nearest-rank p90 of 5 -> index 4
    }

    @Test
    void skipsMissingPublishedAtAndBogusDeltas() {
        Instant base = Instant.parse("2026-07-03T10:00:00Z");
        List<NewsArticleEntity> articles = List.of(
                article(null, base.plusSeconds(10)),               // no publishedAt -> skip
                article(base, base.minusSeconds(30)),              // negative (future-dated feed) -> skip
                article(base, base.plus(Duration.ofDays(5))),      // absurd backfill -> skip
                article(base, base.plusSeconds(45))                // the only valid one
        );

        LatencyService.LatencyStats stats = serviceReturning(articles).detectionLatency();

        assertThat(stats.sampleSize()).isEqualTo(1);
        assertThat(stats.medianSeconds()).isEqualTo(45);
    }

    @Test
    void emptyWhenNoValidSamples() {
        LatencyService.LatencyStats stats = serviceReturning(List.of()).detectionLatency();

        assertThat(stats.sampleSize()).isZero();
        assertThat(stats.medianSeconds()).isNull();
    }
}
