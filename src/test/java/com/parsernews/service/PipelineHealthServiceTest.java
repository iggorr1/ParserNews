package com.parsernews.service;

import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.service.RssFeedHealthService.FeedHealthSummary;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineHealthServiceTest {

    private static NewsArticleEntity articleCollectedAt(Instant when) {
        NewsArticleEntity e = mock(NewsArticleEntity.class);
        when(e.getCollectedAt()).thenReturn(when);
        return e;
    }

    private PipelineHealthService service(int feeds, int unhealthy, Instant newestArticle,
                                          long latencyMedian, int latencySample,
                                          SecFullTextSearchScanner.FtsStatus efts) {
        RssFeedHealthService feedHealth = mock(RssFeedHealthService.class);
        FeedHealthSummary ok = new FeedHealthSummary("u", Instant.now(), null, 0, null, 0);
        FeedHealthSummary bad = new FeedHealthSummary("b", null, Instant.now(), 5, "err", Long.MAX_VALUE);
        when(feedHealth.summaries()).thenReturn(java.util.Collections.nCopies(feeds, ok));
        when(feedHealth.unhealthy()).thenReturn(java.util.Collections.nCopies(unhealthy, bad));

        LatencyService latency = mock(LatencyService.class);
        when(latency.detectionLatency()).thenReturn(new LatencyService.LatencyStats(
                latencySample, latencySample == 0 ? null : latencyMedian, latencyMedian, 1L, latencyMedian));

        NewsArticleEntity article = articleCollectedAt(newestArticle);
        List<NewsArticleEntity> articles = List.of(article);
        NewsArticleRepository repo = mock(NewsArticleRepository.class);
        when(repo.findTop200ByOrderByCollectedAtDesc()).thenReturn(articles);

        SecFullTextSearchScanner scanner = mock(SecFullTextSearchScanner.class);
        when(scanner.status()).thenReturn(efts);

        return new PipelineHealthService(feedHealth, latency, repo, scanner);
    }

    @Test
    void reportsOkWhenAllStagesHealthy() {
        var efts = new SecFullTextSearchScanner.FtsStatus(true, Instant.now(),
                new SecFullTextSearchScanner.FtsSummary(true, 40, 0, 20, List.of()));
        PipelineHealthService.PipelineHealth h =
                service(35, 0, Instant.now().minus(Duration.ofMinutes(2)), 120, 200, efts).health();

        assertThat(h.status()).isEqualTo("OK");
        assertThat(h.warnings()).isEmpty();
    }

    @Test
    void warnsWhenFeedsUnhealthyAndDataStale() {
        var efts = new SecFullTextSearchScanner.FtsStatus(true, Instant.now(),
                new SecFullTextSearchScanner.FtsSummary(true, 0, 0, 0, List.of()));
        PipelineHealthService.PipelineHealth h =
                service(35, 3, Instant.now().minus(Duration.ofHours(2)), 120, 200, efts).health();

        assertThat(h.status()).isEqualTo("WARN");
        assertThat(h.warnings()).anyMatch(w -> w.startsWith("feeds"));
        assertThat(h.warnings()).anyMatch(w -> w.startsWith("scanning"));
    }

    @Test
    void warnsWhenEftsErroredOrStale() {
        var efts = new SecFullTextSearchScanner.FtsStatus(true, Instant.now().minus(Duration.ofHours(1)),
                new SecFullTextSearchScanner.FtsSummary(true, 0, 0, 0, List.of("query x: HTTP 429")));
        PipelineHealthService.PipelineHealth h =
                service(35, 0, Instant.now(), 120, 200, efts).health();

        assertThat(h.status()).isEqualTo("WARN");
        assertThat(h.warnings()).anyMatch(w -> w.startsWith("efts"));
    }
}
