package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "full-refresh.scheduler")
public record FullRefreshSchedulerSettings(
        boolean enabled,
        long initialDelayMs,
        long fixedDelayMs
) {
}
