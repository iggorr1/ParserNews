package com.parsernews.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AlertDispatchScheduler {
    private final AlertDispatchService alertDispatchService;

    public AlertDispatchScheduler(AlertDispatchService alertDispatchService) {
        this.alertDispatchService = alertDispatchService;
    }

    @Scheduled(fixedDelayString = "${alerts.dispatch.fixed-delay-ms:300000}")
    public void dispatch() {
        alertDispatchService.dispatch();
    }
}
