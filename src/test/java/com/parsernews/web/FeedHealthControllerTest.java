package com.parsernews.web;

import com.parsernews.service.RssFeedHealthService;
import com.parsernews.service.RssFeedHealthService.FeedHealthSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedHealthControllerTest {
    @Test
    void feedHealthReturnsOverviewWithTotalsAndUnhealthy() {
        RssFeedHealthService service = mock(RssFeedHealthService.class);
        FeedHealthSummary healthy = new FeedHealthSummary(
                "https://good.example/rss", Instant.now(), null, 0, null, 0);
        FeedHealthSummary broken = new FeedHealthSummary(
                "https://bad.example/rss", null, Instant.now(), 5, "403 Forbidden", Long.MAX_VALUE);
        when(service.summaries()).thenReturn(List.of(healthy, broken));
        when(service.unhealthy()).thenReturn(List.of(broken));

        FeedHealthController controller = new FeedHealthController(service);
        FeedHealthController.FeedHealthOverview overview = controller.feedHealth();

        assertThat(overview.total()).isEqualTo(2);
        assertThat(overview.unhealthyCount()).isEqualTo(1);
        assertThat(overview.unhealthy()).extracting(FeedHealthSummary::feedUrl)
                .containsExactly("https://bad.example/rss");
        assertThat(overview.feeds()).hasSize(2);
    }
}
