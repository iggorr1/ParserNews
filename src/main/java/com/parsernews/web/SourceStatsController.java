package com.parsernews.web;

import com.parsernews.service.SourceStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SourceStatsController {
    private final SourceStatsService sourceStatsService;

    public SourceStatsController(SourceStatsService sourceStatsService) {
        this.sourceStatsService = sourceStatsService;
    }

    @GetMapping("/api/stats/sources")
    public List<SourceStatsService.SourceStatsResponse> sourceStats() {
        return sourceStatsService.sourceStats();
    }
}
