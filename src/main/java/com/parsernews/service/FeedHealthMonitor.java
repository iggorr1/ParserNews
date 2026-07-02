package com.parsernews.service;

import com.parsernews.service.RssFeedHealthService.FeedHealthSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Periodically inspects RSS feed health and announces state changes in the log:
 * a WARN when a feed newly crosses into "unhealthy", an INFO when it recovers.
 *
 * <p>It is deliberately <em>stateful</em>: it only emits on a transition, never once per
 * scan cycle. This is what keeps it from re-creating the per-cycle log spam that these
 * warnings used to cause — a feed that has been down for hours is logged once, not forever.
 */
@Service
public class FeedHealthMonitor {
    private static final Logger log = LoggerFactory.getLogger(FeedHealthMonitor.class);

    private final RssFeedHealthService feedHealthService;

    /** URLs currently considered unhealthy — the set we've already alerted on. */
    private final Set<String> alerted = new LinkedHashSet<>();

    public FeedHealthMonitor(RssFeedHealthService feedHealthService) {
        this.feedHealthService = feedHealthService;
    }

    @Scheduled(
            initialDelayString = "${feed-health.monitor.initial-delay-ms:180000}",
            fixedDelayString = "${feed-health.monitor.fixed-delay-ms:900000}"
    )
    public void scheduledCheck() {
        check();
    }

    /**
     * Compares the current unhealthy feeds against the last observed set and logs the
     * transitions. Returns the transition so callers (and tests) can inspect it.
     */
    public synchronized CheckResult check() {
        Set<String> current = new LinkedHashSet<>();
        for (FeedHealthSummary s : feedHealthService.unhealthy()) {
            current.add(s.feedUrl());
        }

        List<String> newlyUnhealthy = current.stream()
                .filter(url -> !alerted.contains(url))
                .toList();
        List<String> recovered = alerted.stream()
                .filter(url -> !current.contains(url))
                .toList();

        for (String url : newlyUnhealthy) {
            log.warn("RSS feed UNHEALTHY: {} — {}", url, reasonFor(url));
        }
        for (String url : recovered) {
            log.info("RSS feed recovered: {}", url);
        }

        alerted.clear();
        alerted.addAll(current);
        return new CheckResult(newlyUnhealthy, recovered, List.copyOf(current));
    }

    private String reasonFor(String url) {
        return feedHealthService.unhealthy().stream()
                .filter(s -> s.feedUrl().equals(url))
                .findFirst()
                .map(s -> {
                    if (s.consecutiveErrors() >= RssFeedHealthService.ERROR_THRESHOLD) {
                        return s.consecutiveErrors() + " consecutive errors: " + s.lastErrorMessage();
                    }
                    return "no successful fetch in " + s.staleSinceHours() + "h";
                })
                .orElse("unknown");
    }

    public record CheckResult(
            List<String> newlyUnhealthy,
            List<String> recovered,
            List<String> currentlyUnhealthy
    ) {
    }
}
