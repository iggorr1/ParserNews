package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * SEC EDGAR full-text search (EFTS) — finds deals by document content ("definitive agreement to
 * acquire", "agreement and plan of merger", ...) across all filings, catching the early 8-K deal
 * announcements that the form-based getcurrent scanner misses.
 */
@ConfigurationProperties(prefix = "sec.fts")
public record SecFullTextSearchSettings(
        boolean enabled,
        String queries,
        String forms,
        int lookbackDays,
        int maxHitsPerQuery,
        long requestDelayMs,
        Scheduler scheduler
) {
    // High-precision M&A phrases. Semicolon-separated so the phrases themselves can contain commas.
    private static final String DEFAULT_QUERIES =
            "definitive agreement to acquire;agreement and plan of merger;to be acquired by;commence a tender offer";
    private static final String DEFAULT_FORMS = "8-K";

    public SecFullTextSearchSettings {
        queries = queries == null || queries.isBlank() ? DEFAULT_QUERIES : queries;
        forms = forms == null || forms.isBlank() ? DEFAULT_FORMS : forms;
        lookbackDays = lookbackDays <= 0 ? 3 : Math.min(lookbackDays, 30);
        maxHitsPerQuery = maxHitsPerQuery <= 0 ? 30 : Math.min(maxHitsPerQuery, 100);
        requestDelayMs = requestDelayMs < 0 ? 200 : requestDelayMs;
        scheduler = scheduler == null ? new Scheduler(false, 180000, 300000) : scheduler;
    }

    public List<String> queryList() {
        return Arrays.stream(queries.split(";"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public record Scheduler(boolean enabled, long initialDelayMs, long fixedDelayMs) {
    }
}
