package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scanner.analyzer")
public record AnalyzerSettings(
        Integer watchlistThresholdOverride,
        Integer manualReviewThresholdOverride,
        Integer importantThresholdOverride
) {
}
