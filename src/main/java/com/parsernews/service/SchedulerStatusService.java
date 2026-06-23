package com.parsernews.service;

import com.parsernews.config.AlertDispatchSettings;
import com.parsernews.config.TelegramAlertSettings;
import com.parsernews.persistence.ScanRunEntity;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.persistence.ScanRunStatus;
import com.parsernews.persistence.ScanRunTriggerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SchedulerStatusService {
    private final boolean rssMonitoringEnabled;
    private final ScheduledFullRefreshScheduler scheduledFullRefreshScheduler;
    private final ScanRunRepository scanRunRepository;
    private final TelegramAlertSettings telegramAlertSettings;
    private final AlertDispatchSettings alertDispatchSettings;

    public SchedulerStatusService(
            @Value("${scanner.monitoring.enabled:false}") boolean rssMonitoringEnabled,
            ScheduledFullRefreshScheduler scheduledFullRefreshScheduler,
            ScanRunRepository scanRunRepository,
            TelegramAlertSettings telegramAlertSettings,
            AlertDispatchSettings alertDispatchSettings
    ) {
        this.rssMonitoringEnabled = rssMonitoringEnabled;
        this.scheduledFullRefreshScheduler = scheduledFullRefreshScheduler;
        this.scanRunRepository = scanRunRepository;
        this.telegramAlertSettings = telegramAlertSettings;
        this.alertDispatchSettings = alertDispatchSettings;
    }

    @Transactional(readOnly = true)
    public SchedulerStatusResponse status() {
        ScheduledFullRefreshScheduler.State state = scheduledFullRefreshScheduler.state();
        return new SchedulerStatusResponse(
                rssMonitoringEnabled,
                scanRunRepository.findTopByTriggerTypeOrderByStartedAtDesc(ScanRunTriggerType.SCHEDULED)
                        .map(ScheduledRssScanSummary::from)
                        .orElse(null),
                state.enabled(),
                state.running(),
                state.lastStartedAt(),
                state.lastFinishedAt(),
                state.lastSuccess(),
                state.lastWarnings(),
                state.lastError(),
                "SCHEDULED_FULL_REFRESH",
                state.fixedDelayMs(),
                state.nextExpectedRunAt(),
                telegramAlertSettings.enabled(),
                alertDispatchSettings.enabled(),
                schedulerMessage(state)
        );
    }

    private String schedulerMessage(ScheduledFullRefreshScheduler.State state) {
        if (!state.enabled()) {
            return "Full Refresh runs only when you click the button.";
        }
        if (state.running()) {
            return "Scheduled Full Refresh is running.";
        }
        if (Boolean.FALSE.equals(state.lastSuccess())) {
            return "Last scheduled Full Refresh failed.";
        }
        if (Boolean.TRUE.equals(state.lastSuccess())) {
            return "Scheduled Full Refresh is enabled.";
        }
        return "Scheduled Full Refresh is enabled and waiting for first run.";
    }

    public record SchedulerStatusResponse(
            boolean rssMonitoringEnabled,
            ScheduledRssScanSummary latestScheduledRssScan,
            boolean fullRefreshSchedulerEnabled,
            boolean fullRefreshSchedulerRunning,
            Instant lastScheduledFullRefreshStartedAt,
            Instant lastScheduledFullRefreshFinishedAt,
            Boolean lastScheduledFullRefreshSuccess,
            List<String> lastScheduledFullRefreshWarnings,
            String lastScheduledFullRefreshError,
            String scheduledFullRefreshTriggerType,
            long fullRefreshFixedDelayMs,
            Instant nextExpectedFullRefreshAt,
            boolean telegramEnabled,
            boolean dispatchEnabled,
            String message
    ) {
    }

    public record ScheduledRssScanSummary(
            Long id,
            ScanRunStatus status,
            Instant startedAt,
            Instant finishedAt,
            int totalFetched,
            int candidatesFound,
            int duplicatesSkipped
    ) {
        static ScheduledRssScanSummary from(ScanRunEntity scanRun) {
            return new ScheduledRssScanSummary(
                    scanRun.getId(),
                    scanRun.getStatus(),
                    scanRun.getStartedAt(),
                    scanRun.getFinishedAt(),
                    scanRun.getTotalFetched(),
                    scanRun.getCandidatesFound(),
                    scanRun.getDuplicatesSkipped()
            );
        }
    }
}
