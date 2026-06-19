package com.parsernews.service;

import java.util.Locale;

final class NewsTextPatterns {
    static final String ROUNDUP_AGGREGATOR_WARNING = "roundup/aggregator article, not primary source";

    private NewsTextPatterns() {
    }

    static boolean isRoundupAggregator(String... values) {
        String text = String.join(" ", values == null ? new String[0] : values)
                .toLowerCase(Locale.ROOT);
        return text.contains("press releases you need to see this week")
                || text.contains("top press releases")
                || text.contains("news roundup")
                || text.contains("weekly roundup")
                || text.contains("weekly recap")
                || text.contains("in case you missed it");
    }
}
