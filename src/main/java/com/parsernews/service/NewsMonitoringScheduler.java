package com.parsernews.service;

import com.parsernews.persistence.ScanRunTriggerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NewsMonitoringScheduler {
    private final NewsScannerService newsScannerService;
    private final ApplicationEventPublisher events;
    private final boolean enabled;

    public NewsMonitoringScheduler(
            NewsScannerService newsScannerService,
            ApplicationEventPublisher events,
            @Value("${scanner.monitoring.enabled:false}") boolean enabled
    ) {
        this.newsScannerService = newsScannerService;
        this.events = events;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${scanner.monitoring.initial-delay-ms:60000}",
            fixedDelayString = "${scanner.monitoring.poll-delay-ms:300000}"
    )
    public void poll() {
        if (enabled) {
            newsScannerService.scan(ScanRunTriggerType.SCHEDULED);
            // Fire the dispatch pipeline immediately after each fast poll, instead of waiting
            // for the slower full-refresh / SEC-discovery cycles to publish it. This is what
            // lets a tightened poll interval actually shorten end-to-end detection->dispatch time.
            events.publishEvent(new ScanCompletedEvent(this));
        }
    }
}
