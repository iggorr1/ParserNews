package com.parsernews.service;

import com.parsernews.config.AlertDispatchSettings;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.ScanRunEntity;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.persistence.ScanRunStatus;
import com.parsernews.persistence.ScanRunTriggerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StatusService {
    private final boolean scannerMonitoringEnabled;
    private final AlertDispatchSettings alertDispatchSettings;
    private final TelegramRuntimeSettingsService telegramRuntimeSettingsService;
    private final ScanRunRepository scanRunRepository;
    private final NewsArticleRepository articleRepository;
    private final DetectedEventRepository eventRepository;

    public StatusService(
            @Value("${scanner.monitoring.enabled:false}") boolean scannerMonitoringEnabled,
            AlertDispatchSettings alertDispatchSettings,
            TelegramRuntimeSettingsService telegramRuntimeSettingsService,
            ScanRunRepository scanRunRepository,
            NewsArticleRepository articleRepository,
            DetectedEventRepository eventRepository
    ) {
        this.scannerMonitoringEnabled = scannerMonitoringEnabled;
        this.alertDispatchSettings = alertDispatchSettings;
        this.telegramRuntimeSettingsService = telegramRuntimeSettingsService;
        this.scanRunRepository = scanRunRepository;
        this.articleRepository = articleRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public StatusResponse status() {
        ScanRunSummary latestScan = scanRunRepository.findTopByOrderByStartedAtDesc()
                .map(ScanRunSummary::from)
                .orElse(null);
        TelegramRuntimeSettingsService.EffectiveTelegramSettings telegramSettings = telegramRuntimeSettingsService.effectiveSettings();
        return new StatusResponse(
                health(latestScan),
                new ConfigSummary(
                        scannerMonitoringEnabled,
                        alertDispatchSettings.enabled(),
                        telegramSettings.enabled(),
                        telegramSettings.configured()
                ),
                latestScan,
                new ArticleEventStats(
                        articleRepository.count(),
                        eventRepository.countByCandidateStrengthNot(CandidateStrength.NONE),
                        eventRepository.countByCandidateStrength(CandidateStrength.HIGH),
                        eventRepository.countByCandidateStrength(CandidateStrength.MEDIUM),
                        eventRepository.countByCandidateStrength(CandidateStrength.LOW)
                ),
                new AlertStats(
                        eventRepository.countByAlertEligibleTrue(),
                        eventRepository.countByAlertQueuedAtIsNotNull()
                )
        );
    }

    private HealthStatus health(ScanRunSummary latestScan) {
        if (latestScan != null && latestScan.status() == ScanRunStatus.FAILED) {
            return HealthStatus.WARN;
        }
        if (scannerMonitoringEnabled && latestScan == null) {
            return HealthStatus.WARN;
        }
        return HealthStatus.OK;
    }

    public enum HealthStatus {
        OK,
        WARN
    }

    public record StatusResponse(
            HealthStatus status,
            ConfigSummary config,
            ScanRunSummary latestScan,
            ArticleEventStats articleEvents,
            AlertStats alerts
    ) {
    }

    public record ConfigSummary(
            boolean scannerMonitoringEnabled,
            boolean alertDispatchEnabled,
            boolean telegramEnabled,
            boolean telegramConfigured
    ) {
    }

    public record ScanRunSummary(
            Long id,
            ScanRunStatus status,
            Instant startedAt,
            Instant finishedAt,
            ScanRunTriggerType triggerType,
            int totalFetched,
            int candidatesFound,
            int duplicatesSkipped
    ) {
        static ScanRunSummary from(ScanRunEntity scanRun) {
            return new ScanRunSummary(
                    scanRun.getId(),
                    scanRun.getStatus(),
                    scanRun.getStartedAt(),
                    scanRun.getFinishedAt(),
                    scanRun.getTriggerType(),
                    scanRun.getTotalFetched(),
                    scanRun.getCandidatesFound(),
                    scanRun.getDuplicatesSkipped()
            );
        }
    }

    public record ArticleEventStats(
            long totalSavedArticles,
            long totalDetectedCandidates,
            long highCandidateCount,
            long mediumCandidateCount,
            long lowCandidateCount
    ) {
    }

    public record AlertStats(
            long alertEligibleCount,
            long alreadyQueuedCount
    ) {
    }
}
