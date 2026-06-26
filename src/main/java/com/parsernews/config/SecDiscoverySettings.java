package com.parsernews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "sec.discovery")
public record SecDiscoverySettings(
        boolean enabled,
        int maxFilingsPerRun,
        String forms,
        boolean fetchPrimaryDocument,
        long requestDelayMs,
        Scheduler scheduler
) {
    private static final String DEFAULT_FORMS = "8-K,SC TO-T,SC TO-I,SC 14D9,DEFM14A,PREM14A,425,S-4";

    public SecDiscoverySettings {
        maxFilingsPerRun = maxFilingsPerRun <= 0 ? 50 : Math.min(maxFilingsPerRun, 200);
        forms = forms == null || forms.isBlank() ? DEFAULT_FORMS : forms;
        requestDelayMs = requestDelayMs < 0 ? 150 : requestDelayMs;
        scheduler = scheduler == null ? new Scheduler(false, 120000, 300000) : scheduler;
    }

    public List<String> formList() {
        return Arrays.stream(forms.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .toList();
    }

    public record Scheduler(
            boolean enabled,
            long initialDelayMs,
            long fixedDelayMs
    ) {
        public Scheduler {
            initialDelayMs = initialDelayMs <= 0 ? 120000 : initialDelayMs;
            fixedDelayMs = fixedDelayMs <= 0 ? 300000 : fixedDelayMs;
        }
    }
}
