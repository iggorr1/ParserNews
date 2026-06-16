package com.parsernews.model;

public record ScanSummary(
        int totalRead,
        int analyzed,
        int duplicatesSkipped,
        int labeled,
        int matchedExpected,
        int mismatchedExpected
) {
}
