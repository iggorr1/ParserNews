package com.parsernews.service;

import com.parsernews.persistence.NewsArticleEntity;
import com.parsernews.persistence.NewsArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures detection latency — how long between a news item's publish time
 * ({@code publishedAt}, from the feed) and the moment we first stored it
 * ({@code collectedAt}). This captures the two dominant delays in the pipeline:
 * the feed provider's own lag plus our polling interval. It is the key number
 * for judging whether tightening the polling cadence actually helps.
 */
@Service
public class LatencyService {
    // Ignore samples whose publish time is missing or clearly bogus (parse errors,
    // future-dated feeds, or historical backfill that would skew the percentiles).
    private static final long MAX_REASONABLE_SECONDS = Duration.ofDays(2).toSeconds();

    private final NewsArticleRepository articleRepository;

    public LatencyService(NewsArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public LatencyStats detectionLatency() {
        List<Long> seconds = new ArrayList<>();
        for (NewsArticleEntity article : articleRepository.findTop200ByOrderByCollectedAtDesc()) {
            Instant published = article.getPublishedAt();
            Instant collected = article.getCollectedAt();
            if (published == null || collected == null) {
                continue;
            }
            long delta = Duration.between(published, collected).getSeconds();
            if (delta < 0 || delta > MAX_REASONABLE_SECONDS) {
                continue;
            }
            seconds.add(delta);
        }
        return LatencyStats.from(seconds);
    }

    public record LatencyStats(
            int sampleSize,
            Long medianSeconds,
            Long p90Seconds,
            Long fastestSeconds,
            Long slowestSeconds
    ) {
        static LatencyStats from(List<Long> seconds) {
            if (seconds.isEmpty()) {
                return new LatencyStats(0, null, null, null, null);
            }
            List<Long> sorted = seconds.stream().sorted().toList();
            return new LatencyStats(
                    sorted.size(),
                    percentile(sorted, 50),
                    percentile(sorted, 90),
                    sorted.get(0),
                    sorted.get(sorted.size() - 1)
            );
        }

        private static long percentile(List<Long> sorted, int p) {
            // Nearest-rank percentile.
            int rank = (int) Math.ceil(p / 100.0 * sorted.size());
            int index = Math.min(Math.max(rank - 1, 0), sorted.size() - 1);
            return sorted.get(index);
        }
    }
}
