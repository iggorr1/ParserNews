package com.parsernews.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.config.AlertDispatchSettings;
import com.parsernews.config.TelegramAlertSettings;
import com.parsernews.persistence.CandidateStrength;
import com.parsernews.persistence.DetectedEventRepository;
import com.parsernews.persistence.NewsArticleRepository;
import com.parsernews.persistence.ScanRunEntity;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.persistence.ScanRunStatus;
import com.parsernews.persistence.ScanRunTriggerType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusServiceTest {
    @Test
    void statusReturnsBasicConfigFlagsAndCounts() {
        ScanRunEntity latestScan = successfulScanRun(10L);
        StatusService service = service(false, true, true, "secret-token", "secret-chat", Optional.of(latestScan));

        StatusService.StatusResponse response = service.status();

        assertThat(response.status()).isEqualTo(StatusService.HealthStatus.OK);
        assertThat(response.config().scannerMonitoringEnabled()).isFalse();
        assertThat(response.config().alertDispatchEnabled()).isTrue();
        assertThat(response.config().telegramEnabled()).isTrue();
        assertThat(response.config().telegramConfigured()).isTrue();
        assertThat(response.articleEvents().totalSavedArticles()).isEqualTo(7);
        assertThat(response.articleEvents().totalDetectedCandidates()).isEqualTo(6);
        assertThat(response.articleEvents().highCandidateCount()).isEqualTo(3);
        assertThat(response.articleEvents().mediumCandidateCount()).isEqualTo(2);
        assertThat(response.articleEvents().lowCandidateCount()).isEqualTo(1);
        assertThat(response.alerts().alertEligibleCount()).isEqualTo(4);
        assertThat(response.alerts().alreadyQueuedCount()).isEqualTo(5);
    }

    @Test
    void statusIncludesLatestScanRunSummary() {
        ScanRunEntity latestScan = successfulScanRun(20L);
        StatusService service = service(false, false, false, "", "", Optional.of(latestScan));

        StatusService.StatusResponse response = service.status();

        assertThat(response.latestScan().id()).isEqualTo(20L);
        assertThat(response.latestScan().status()).isEqualTo(ScanRunStatus.SUCCESS);
        assertThat(response.latestScan().triggerType()).isEqualTo(ScanRunTriggerType.MANUAL);
        assertThat(response.latestScan().totalFetched()).isEqualTo(12);
        assertThat(response.latestScan().candidatesFound()).isEqualTo(3);
        assertThat(response.latestScan().duplicatesSkipped()).isEqualTo(2);
        assertThat(response.latestScan().startedAt()).isNotNull();
        assertThat(response.latestScan().finishedAt()).isNotNull();
    }

    @Test
    void statusWarnsWhenLatestScanFailed() {
        ScanRunEntity latestScan = new ScanRunEntity(ScanRunTriggerType.SCHEDULED);
        latestScan.markFailed("RSS failed");
        setId(latestScan, 30L);
        StatusService service = service(false, false, false, "", "", Optional.of(latestScan));

        StatusService.StatusResponse response = service.status();

        assertThat(response.status()).isEqualTo(StatusService.HealthStatus.WARN);
        assertThat(response.latestScan().status()).isEqualTo(ScanRunStatus.FAILED);
    }

    @Test
    void statusWarnsWhenMonitoringEnabledButNoScanHasRun() {
        StatusService service = service(true, false, false, "", "", Optional.empty());

        StatusService.StatusResponse response = service.status();

        assertThat(response.status()).isEqualTo(StatusService.HealthStatus.WARN);
        assertThat(response.latestScan()).isNull();
    }

    @Test
    void statusDoesNotExposeTelegramSecrets() throws JsonProcessingException {
        StatusService service = service(false, true, true, "secret-token", "secret-chat", Optional.empty());
        ObjectMapper objectMapper = new ObjectMapper();

        String json = objectMapper.writeValueAsString(service.status());

        assertThat(json).doesNotContain("secret-token");
        assertThat(json).doesNotContain("secret-chat");
        assertThat(json).contains("telegramEnabled");
        assertThat(json).contains("telegramConfigured");
    }

    private StatusService service(
            boolean monitoringEnabled,
            boolean dispatchEnabled,
            boolean telegramEnabled,
            String botToken,
            String chatId,
            Optional<ScanRunEntity> latestScan
    ) {
        ScanRunRepository scanRunRepository = mock(ScanRunRepository.class);
        NewsArticleRepository articleRepository = mock(NewsArticleRepository.class);
        DetectedEventRepository eventRepository = mock(DetectedEventRepository.class);
        when(scanRunRepository.findTopByOrderByStartedAtDesc()).thenReturn(latestScan);
        when(articleRepository.count()).thenReturn(7L);
        when(eventRepository.countByCandidateStrengthNot(CandidateStrength.NONE)).thenReturn(6L);
        when(eventRepository.countByCandidateStrength(CandidateStrength.HIGH)).thenReturn(3L);
        when(eventRepository.countByCandidateStrength(CandidateStrength.MEDIUM)).thenReturn(2L);
        when(eventRepository.countByCandidateStrength(CandidateStrength.LOW)).thenReturn(1L);
        when(eventRepository.countByAlertEligibleTrue()).thenReturn(4L);
        when(eventRepository.countByAlertQueuedAtIsNotNull()).thenReturn(5L);
        return new StatusService(
                monitoringEnabled,
                new AlertDispatchSettings(dispatchEnabled, 300000, 5),
                new TelegramAlertSettings(telegramEnabled, botToken, chatId),
                scanRunRepository,
                articleRepository,
                eventRepository
        );
    }

    private ScanRunEntity successfulScanRun(Long id) {
        ScanRunEntity scanRun = new ScanRunEntity(ScanRunTriggerType.MANUAL);
        scanRun.markSuccess(12, 3, 4, 2);
        setId(scanRun, id);
        return scanRun;
    }

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
