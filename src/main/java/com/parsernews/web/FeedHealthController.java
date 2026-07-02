package com.parsernews.web;

import com.parsernews.service.RssFeedHealthService;
import com.parsernews.service.RssFeedHealthService.FeedHealthSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Surfaces RSS feed health so silent fetch failures (e.g. an auth block that makes a
 * feed 403 forever) become visible without grepping logs. ADMIN-only via SecurityConfig
 * ({@code /api/admin/**}).
 */
@RestController
public class FeedHealthController {
    private final RssFeedHealthService feedHealthService;

    public FeedHealthController(RssFeedHealthService feedHealthService) {
        this.feedHealthService = feedHealthService;
    }

    @GetMapping("/api/admin/feeds/health")
    public FeedHealthOverview feedHealth() {
        List<FeedHealthSummary> all = feedHealthService.summaries();
        List<FeedHealthSummary> unhealthy = feedHealthService.unhealthy();
        return new FeedHealthOverview(all.size(), unhealthy.size(), unhealthy, all);
    }

    public record FeedHealthOverview(
            int total,
            int unhealthyCount,
            List<FeedHealthSummary> unhealthy,
            List<FeedHealthSummary> feeds
    ) {
    }
}
