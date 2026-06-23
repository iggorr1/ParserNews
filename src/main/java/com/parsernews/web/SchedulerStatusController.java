package com.parsernews.web;

import com.parsernews.service.SchedulerStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SchedulerStatusController {
    private final SchedulerStatusService schedulerStatusService;

    public SchedulerStatusController(SchedulerStatusService schedulerStatusService) {
        this.schedulerStatusService = schedulerStatusService;
    }

    @GetMapping("/api/admin/scheduler-status")
    public SchedulerStatusService.SchedulerStatusResponse status() {
        return schedulerStatusService.status();
    }
}
