package com.parsernews.web;

import com.parsernews.service.LatencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes detection-latency stats (wire publish time -> first collected) so the
 * dashboard can show how far behind the feeds the scanner is running.
 */
@RestController
public class LatencyController {
    private final LatencyService latencyService;

    public LatencyController(LatencyService latencyService) {
        this.latencyService = latencyService;
    }

    @GetMapping("/api/latency")
    public LatencyService.LatencyStats latency() {
        return latencyService.detectionLatency();
    }
}
