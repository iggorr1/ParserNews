package com.parsernews.service;

import com.parsernews.service.RssFeedHealthService.FeedHealthSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedHealthMonitorTest {

    private static FeedHealthSummary unhealthy(String url) {
        return new FeedHealthSummary(url, null, Instant.now(), 5, "403 Forbidden", Long.MAX_VALUE);
    }

    @Test
    void firstSightingOfUnhealthyFeedIsReportedAsNew() {
        RssFeedHealthService service = mock(RssFeedHealthService.class);
        when(service.unhealthy()).thenReturn(List.of(unhealthy("https://a.example/rss")));
        FeedHealthMonitor monitor = new FeedHealthMonitor(service);

        FeedHealthMonitor.CheckResult result = monitor.check();

        assertThat(result.newlyUnhealthy()).containsExactly("https://a.example/rss");
        assertThat(result.recovered()).isEmpty();
        assertThat(result.currentlyUnhealthy()).containsExactly("https://a.example/rss");
    }

    @Test
    void alreadyKnownUnhealthyFeedIsNotReportedAgain() {
        RssFeedHealthService service = mock(RssFeedHealthService.class);
        when(service.unhealthy()).thenReturn(List.of(unhealthy("https://a.example/rss")));
        FeedHealthMonitor monitor = new FeedHealthMonitor(service);

        monitor.check(); // first sighting
        FeedHealthMonitor.CheckResult second = monitor.check();

        assertThat(second.newlyUnhealthy()).isEmpty();
        assertThat(second.recovered()).isEmpty();
        assertThat(second.currentlyUnhealthy()).containsExactly("https://a.example/rss");
    }

    @Test
    void feedLeavingUnhealthySetIsReportedAsRecovered() {
        RssFeedHealthService service = mock(RssFeedHealthService.class);
        when(service.unhealthy())
                .thenReturn(List.of(unhealthy("https://a.example/rss")))
                .thenReturn(List.of());
        FeedHealthMonitor monitor = new FeedHealthMonitor(service);

        monitor.check(); // a is unhealthy
        FeedHealthMonitor.CheckResult second = monitor.check(); // a recovered

        assertThat(second.recovered()).containsExactly("https://a.example/rss");
        assertThat(second.newlyUnhealthy()).isEmpty();
        assertThat(second.currentlyUnhealthy()).isEmpty();
    }
}
