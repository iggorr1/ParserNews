package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alerts.dispatch")
public record AlertDispatchSettings(
        boolean enabled,
        long fixedDelayMs,
        int batchSize
) {
}
