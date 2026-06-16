package com.parsernews.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NewsMonitoringScheduler {
    private final NewsScannerService newsScannerService;
    private final boolean enabled;

    public NewsMonitoringScheduler(
            NewsScannerService newsScannerService,
            @Value("${scanner.monitoring.enabled:false}") boolean enabled
    ) {
        this.newsScannerService = newsScannerService;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${scanner.monitoring.initial-delay-ms:60000}",
            fixedDelayString = "${scanner.monitoring.poll-delay-ms:300000}"
    )
    public void poll() {
        if (enabled) {
            newsScannerService.scan();
        }
    }
}
