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
 * One-stop pipeline health: aggregates each stage (feeds -> scanning -> detection latency -> EFTS)
 * into a single OK/WARN view with a plain-English reason per check, so "what's wrong" is obvious
 * at a glance instead of grepping logs across services.
 */
@Service
public class PipelineHealthService {
    private static final long STALE_DATA_MINUTES = 30;
    private static final long HIGH_LATENCY_SECONDS = 1800; // 30 min
    private static final long EFTS_STALE_MINUTES = 20;

    private final RssFeedHealthService feedHealthService;
    private final LatencyService latencyService;
    private final NewsArticleRepository articleRepository;
    private final SecFullTextSearchScanner ftsScanner;

    public PipelineHealthService(
            RssFeedHealthService feedHealthService,
            LatencyService latencyService,
            NewsArticleRepository articleRepository,
            SecFullTextSearchScanner ftsScanner
    ) {
        this.feedHealthService = feedHealthService;
        this.latencyService = latencyService;
        this.articleRepository = articleRepository;
        this.ftsScanner = ftsScanner;
    }

    @Transactional(readOnly = true)
    public PipelineHealth health() {
        List<Check> checks = new ArrayList<>();
        checks.add(feedsCheck());
        checks.add(dataFreshnessCheck());
        checks.add(latencyCheck());
        checks.add(eftsCheck());

        List<String> warnings = checks.stream()
                .filter(c -> !c.ok())
                .map(c -> c.name() + ": " + c.detail())
                .toList();
        String status = warnings.isEmpty() ? "OK" : "WARN";
        return new PipelineHealth(status, checks, warnings);
    }

    private Check feedsCheck() {
        int total = feedHealthService.summaries().size();
        int unhealthy = feedHealthService.unhealthy().size();
        return new Check("feeds", unhealthy == 0,
                total + " feeds, " + unhealthy + " unhealthy");
    }

    private Check dataFreshnessCheck() {
        Instant newest = articleRepository.findTop200ByOrderByCollectedAtDesc().stream()
                .map(NewsArticleEntity::getCollectedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        if (newest == null) {
            return new Check("scanning", false, "no articles collected yet");
        }
        long minutes = Duration.between(newest, Instant.now()).toMinutes();
        return new Check("scanning", minutes <= STALE_DATA_MINUTES,
                "last article collected " + minutes + "m ago");
    }

    private Check latencyCheck() {
        LatencyService.LatencyStats stats = latencyService.detectionLatency();
        if (stats.sampleSize() == 0 || stats.medianSeconds() == null) {
            return new Check("latency", true, "no dated samples yet");
        }
        long median = stats.medianSeconds();
        return new Check("latency", median <= HIGH_LATENCY_SECONDS,
                "median " + median + "s (n=" + stats.sampleSize() + ")");
    }

    private Check eftsCheck() {
        SecFullTextSearchScanner.FtsStatus status = ftsScanner.status();
        if (!status.enabled()) {
            return new Check("efts", true, "disabled");
        }
        if (status.lastRunAt() == null) {
            return new Check("efts", true, "enabled, not run yet");
        }
        long minutes = Duration.between(status.lastRunAt(), Instant.now()).toMinutes();
        int errors = status.lastSummary() == null ? 0 : status.lastSummary().errors().size();
        boolean ok = minutes <= EFTS_STALE_MINUTES && errors == 0;
        String detail = "last run " + minutes + "m ago"
                + (status.lastSummary() != null
                    ? ", upgraded " + status.lastSummary().upgraded() + ", errors " + errors
                    : "");
        return new Check("efts", ok, detail);
    }

    public record PipelineHealth(String status, List<Check> checks, List<String> warnings) {
    }

    public record Check(String name, boolean ok, String detail) {
    }
}
