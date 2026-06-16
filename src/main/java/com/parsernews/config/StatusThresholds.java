package com.parsernews.config;

public record StatusThresholds(
        int watchlist,
        int manualReview,
        int important
) {
}
