package com.parsernews.service;

import com.parsernews.config.FullRefreshSchedulerSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ScheduledFullRefreshScheduler {
    private final FullRefreshService fullRefreshService;
    private final FullRefreshSchedulerSettings settings;
    private final ApplicationEventPublisher events;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Instant lastStartedAt;
    private volatile Instant lastFinishedAt;
    private volatile Boolean lastSuccess;
    private volatile List<String> lastWarnings = List.of();
    private volatile String lastError;

    public ScheduledFullRefreshScheduler(
            FullRefreshService fullRefreshService,
            FullRefreshSchedulerSettings settings,
            ApplicationEventPublisher events
    ) {
        this.fullRefreshService = fullRefreshService;
        this.settings = settings;
        this.events = events;
    }

    @Scheduled(
            initialDelayString = "${full-refresh.scheduler.initial-delay-ms:120000}",
            fixedDelayString = "${full-refresh.scheduler.fixed-delay-ms:900000}"
    )
    public void runScheduledFullRefresh() {
        runOnce();
    }

    public boolean runOnce() {
        if (!settings.enabled()) {
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        lastStartedAt = Instant.now();
        lastFinishedAt = null;
        lastSuccess = null;
        lastError = null;
        try {
            FullRefreshService.FullRefreshSummary summary = fullRefreshService.fullRefresh();
            lastFinishedAt = summary.finishedAt();
            lastSuccess = summary.success();
            lastWarnings = summary.warnings() == null ? List.of() : List.copyOf(summary.warnings());
            lastError = summary.errors() == null || summary.errors().isEmpty()
                    ? null
                    : String.join("; ", summary.errors());
            events.publishEvent(new ScanCompletedEvent(this));
        } catch (RuntimeException exception) {
            lastFinishedAt = Instant.now();
            lastSuccess = false;
            lastError = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
        } finally {
            running.set(false);
        }
        return true;
    }

    public State state() {
        Instant nextExpectedRunAt = null;
        if (settings.enabled() && lastFinishedAt != null) {
            nextExpectedRunAt = lastFinishedAt.plusMillis(settings.fixedDelayMs());
        }
        return new State(
                settings.enabled(),
                running.get(),
                lastStartedAt,
                lastFinishedAt,
                lastSuccess,
                lastWarnings,
                lastError,
                settings.initialDelayMs(),
                settings.fixedDelayMs(),
                nextExpectedRunAt
        );
    }

    public record State(
            boolean enabled,
            boolean running,
            Instant lastStartedAt,
            Instant lastFinishedAt,
            Boolean lastSuccess,
            List<String> lastWarnings,
            String lastError,
            long initialDelayMs,
            long fixedDelayMs,
            Instant nextExpectedRunAt
    ) {
    }
}
