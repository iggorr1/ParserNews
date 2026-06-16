package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "scanner.rss")
public record RssSettings(
        List<String> urls,
        int maxItemsPerFeed,
        int timeoutSeconds,
        boolean fetchFullArticleText,
        List<String> articleWhitelistHosts
) {
}
