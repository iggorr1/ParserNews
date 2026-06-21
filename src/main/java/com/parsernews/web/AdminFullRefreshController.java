package com.parsernews.web;

import com.parsernews.service.FullRefreshService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminFullRefreshController {
    private final FullRefreshService fullRefreshService;

    public AdminFullRefreshController(FullRefreshService fullRefreshService) {
        this.fullRefreshService = fullRefreshService;
    }

    @PostMapping("/api/admin/full-refresh")
    public FullRefreshService.FullRefreshSummary fullRefresh() {
        return fullRefreshService.fullRefresh();
    }
}
