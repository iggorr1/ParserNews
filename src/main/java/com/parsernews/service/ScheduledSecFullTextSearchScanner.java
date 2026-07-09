package com.parsernews.service;

import com.parsernews.config.SecFullTextSearchSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ScheduledSecFullTextSearchScanner {
    private final SecFullTextSearchSettings settings;
    private final SecFullTextSearchScanner scanner;
    private final ApplicationEventPublisher events;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ScheduledSecFullTextSearchScanner(
            SecFullTextSearchSettings settings,
            SecFullTextSearchScanner scanner,
            ApplicationEventPublisher events
    ) {
        this.settings = settings;
        this.scanner = scanner;
        this.events = events;
    }

    @Scheduled(
            initialDelayString = "${sec.fts.scheduler.initial-delay-ms:180000}",
            fixedDelayString = "${sec.fts.scheduler.fixed-delay-ms:300000}"
    )
    public void runScheduledSearch() {
        if (!settings.enabled() || !settings.scheduler().enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            SecFullTextSearchScanner.FtsSummary summary = scanner.scan();
            if (summary.changed() > 0) {
                events.publishEvent(new ScanCompletedEvent(this));
            }
        } finally {
            running.set(false);
        }
    }
}
