package com.parsernews.service;

import com.parsernews.config.AlertDispatchSettings;
import com.parsernews.config.FullRefreshSchedulerSettings;
import com.parsernews.config.TelegramAlertSettings;
import com.parsernews.persistence.ScanRunEntity;
import com.parsernews.persistence.ScanRunRepository;
import com.parsernews.persistence.ScanRunTriggerType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulerStatusServiceTest {
    @Test
    void statusShowsDisabledFullRefreshSchedulerByDefault() {
        SchedulerStatusService service = service(false, scheduler(false), Optional.empty());

        SchedulerStatusService.SchedulerStatusResponse response = service.status();

        assertThat(response.fullRefreshSchedulerEnabled()).isFalse();
        assertThat(response.fullRefreshSchedulerRunning()).isFalse();
        assertThat(response.message()).isEqualTo("Full Refresh runs only when you click the button.");
        assertThat(response.nextExpectedFullRefreshAt()).isNull();
    }

    @Test
    void statusIncludesLatestScheduledRssScan() {
        ScanRunEntity scanRun = new ScanRunEntity(ScanRunTriggerType.SCHEDULED);
        scanRun.markSuccess(20, 3, 5, 4);
        setId(scanRun, 12L);
        SchedulerStatusService service = service(true, scheduler(false), Optional.of(scanRun));

        SchedulerStatusService.SchedulerStatusResponse response = service.status();

        assertThat(response.rssMonitoringEnabled()).isTrue();
        assertThat(response.latestScheduledRssScan().id()).isEqualTo(12L);
        assertThat(response.latestScheduledRssScan().totalFetched()).isEqualTo(20);
        assertThat(response.latestScheduledRssScan().candidatesFound()).isEqualTo(3);
    }

    private SchedulerStatusService service(
            boolean rssMonitoringEnabled,
            ScheduledFullRefreshScheduler scheduler,
            Optional<ScanRunEntity> latestScheduledScan
    ) {
        ScanRunRepository scanRunRepository = mock(ScanRunRepository.class);
        when(scanRunRepository.findTopByTriggerTypeOrderByStartedAtDesc(ScanRunTriggerType.SCHEDULED))
                .thenReturn(latestScheduledScan);
        return new SchedulerStatusService(
                rssMonitoringEnabled,
                scheduler,
                scanRunRepository,
                new TelegramAlertSettings(false, "", ""),
                new AlertDispatchSettings(false, 300000, 5)
        );
    }

    private ScheduledFullRefreshScheduler scheduler(boolean enabled) {
        return new ScheduledFullRefreshScheduler(
                mock(FullRefreshService.class),
                new FullRefreshSchedulerSettings(enabled, 120000, 900000)
        );
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
