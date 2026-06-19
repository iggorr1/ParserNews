package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "sec.scanner")
public record SecScannerSettings(
        boolean enabled,
        String watchlist,
        int maxFilingsPerCik
) {
    public SecScannerSettings {
        maxFilingsPerCik = maxFilingsPerCik <= 0 ? 20 : maxFilingsPerCik;
    }

    public List<String> watchlistCiks() {
        if (watchlist == null || watchlist.isBlank()) {
            return List.of();
        }
        return Arrays.stream(watchlist.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
