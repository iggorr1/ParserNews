package com.parsernews.service;

import com.parsernews.config.SecDiscoverySettings;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ScheduledSecDiscoveryScannerTest {
    @Test
    void disabledSchedulerDoesNotRunDiscovery() {
        SecDiscoveryScanner scanner = mock(SecDiscoveryScanner.class);
        ScheduledSecDiscoveryScanner scheduler = new ScheduledSecDiscoveryScanner(
                settings(true, false),
                scanner,
                mock(ApplicationEventPublisher.class)
        );

        scheduler.runScheduledDiscovery();

        verify(scanner, never()).scan();
    }

    @Test
    void disabledDiscoveryDoesNotRunScheduledDiscovery() {
        SecDiscoveryScanner scanner = mock(SecDiscoveryScanner.class);
        ScheduledSecDiscoveryScanner scheduler = new ScheduledSecDiscoveryScanner(
                settings(false, true),
                scanner,
                mock(ApplicationEventPublisher.class)
        );

        scheduler.runScheduledDiscovery();

        verify(scanner, never()).scan();
    }

    @Test
    void enabledSchedulerRunsDiscovery() {
        SecDiscoveryScanner scanner = mock(SecDiscoveryScanner.class);
        ScheduledSecDiscoveryScanner scheduler = new ScheduledSecDiscoveryScanner(
                settings(true, true),
                scanner,
                mock(ApplicationEventPublisher.class)
        );

        scheduler.runScheduledDiscovery();

        verify(scanner).scan();
    }

    private SecDiscoverySettings settings(boolean enabled, boolean schedulerEnabled) {
        return new SecDiscoverySettings(
                enabled,
                50,
                "8-K",
                false,
                0,
                new SecDiscoverySettings.Scheduler(schedulerEnabled, 120000, 300000)
        );
    }
}
