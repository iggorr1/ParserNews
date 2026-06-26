package com.parsernews.service;

import com.parsernews.config.SecDiscoverySettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ScheduledSecDiscoveryScanner {
    private final SecDiscoverySettings settings;
    private final SecDiscoveryScanner discoveryScanner;
    private final ApplicationEventPublisher events;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ScheduledSecDiscoveryScanner(
            SecDiscoverySettings settings,
            SecDiscoveryScanner discoveryScanner,
            ApplicationEventPublisher events
    ) {
        this.settings = settings;
        this.discoveryScanner = discoveryScanner;
        this.events = events;
    }

    @Scheduled(
            initialDelayString = "${sec.discovery.scheduler.initial-delay-ms:120000}",
            fixedDelayString = "${sec.discovery.scheduler.fixed-delay-ms:300000}"
    )
    public void runScheduledDiscovery() {
        if (!settings.enabled() || !settings.scheduler().enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            discoveryScanner.scan();
            events.publishEvent(new ScanCompletedEvent(this));
        } finally {
            running.set(false);
        }
    }
}
