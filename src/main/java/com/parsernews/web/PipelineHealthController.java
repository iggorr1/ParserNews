package com.parsernews.web;

import com.parsernews.service.PipelineHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single pipeline-health view: {@code GET /api/pipeline/health} returns OK/WARN plus a per-stage
 * reason, so diagnosing "what's wrong" doesn't require reading logs across services.
 */
@RestController
public class PipelineHealthController {
    private final PipelineHealthService pipelineHealthService;

    public PipelineHealthController(PipelineHealthService pipelineHealthService) {
        this.pipelineHealthService = pipelineHealthService;
    }

    @GetMapping("/api/pipeline/health")
    public PipelineHealthService.PipelineHealth health() {
        return pipelineHealthService.health();
    }
}
