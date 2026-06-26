package com.parsernews.service;

import com.parsernews.config.FullRefreshSchedulerSettings;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledFullRefreshSchedulerTest {
    @Test
    void disabledSchedulerDoesNotRunFullRefresh() {
        FullRefreshService fullRefreshService = mock(FullRefreshService.class);
        ScheduledFullRefreshScheduler scheduler = new ScheduledFullRefreshScheduler(
                fullRefreshService,
                new FullRefreshSchedulerSettings(false, 120000, 900000),
                mock(ApplicationEventPublisher.class)
        );

        boolean started = scheduler.runOnce();

        assertThat(started).isFalse();
        verify(fullRefreshService, never()).fullRefresh();
        assertThat(scheduler.state().enabled()).isFalse();
    }

    @Test
    void enabledSchedulerRunsFullRefreshAndRecordsState() {
        FullRefreshService fullRefreshService = mock(FullRefreshService.class);
        when(fullRefreshService.fullRefresh()).thenReturn(summary(true, List.of("SEC scanner disabled or watchlist empty"), List.of()));
        ScheduledFullRefreshScheduler scheduler = new ScheduledFullRefreshScheduler(
                fullRefreshService,
                new FullRefreshSchedulerSettings(true, 120000, 900000),
                mock(ApplicationEventPublisher.class)
        );

        boolean started = scheduler.runOnce();

        ScheduledFullRefreshScheduler.State state = scheduler.state();
        assertThat(started).isTrue();
        assertThat(state.lastStartedAt()).isNotNull();
        assertThat(state.lastFinishedAt()).isNotNull();
        assertThat(state.lastSuccess()).isTrue();
        assertThat(state.lastWarnings()).contains("SEC scanner disabled or watchlist empty");
        assertThat(state.nextExpectedRunAt()).isEqualTo(state.lastFinishedAt().plusMillis(900000));
        verify(fullRefreshService).fullRefresh();
    }

    @Test
    void schedulerPreventsOverlappingRuns() throws InterruptedException {
        BlockingFullRefreshService fullRefreshService = new BlockingFullRefreshService();
        ScheduledFullRefreshScheduler scheduler = new ScheduledFullRefreshScheduler(
                fullRefreshService,
                new FullRefreshSchedulerSettings(true, 120000, 900000),
                mock(ApplicationEventPublisher.class)
        );
        Thread firstRun = new Thread(scheduler::runOnce);
        firstRun.start();
        assertThat(fullRefreshService.started.await(2, TimeUnit.SECONDS)).isTrue();

        boolean secondStarted = scheduler.runOnce();

        fullRefreshService.release.countDown();
        firstRun.join(2000);
        assertThat(secondStarted).isFalse();
        assertThat(fullRefreshService.calls.get()).isEqualTo(1);
        assertThat(scheduler.state().running()).isFalse();
    }

    private FullRefreshService.FullRefreshSummary summary(boolean success, List<String> warnings, List<String> errors) {
        Instant now = Instant.parse("2026-06-23T10:00:00Z");
        return new FullRefreshService.FullRefreshSummary(
                now,
                now.plusSeconds(2),
                null,
                null,
                null,
                null,
                warnings,
                errors,
                success
        );
    }

    private static class BlockingFullRefreshService extends FullRefreshService {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        BlockingFullRefreshService() {
            super(null, null, null, null);
        }

        @Override
        public FullRefreshSummary fullRefresh() {
            calls.incrementAndGet();
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            Instant now = Instant.now();
            return new FullRefreshSummary(now, now, null, null, null, null, List.of(), List.of(), true);
        }
    }
}
