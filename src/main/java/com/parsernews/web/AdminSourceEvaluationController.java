package com.parsernews.web;

import com.parsernews.service.SourceEvaluationPreviewService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminSourceEvaluationController {
    private final SourceEvaluationPreviewService sourceEvaluationPreviewService;

    public AdminSourceEvaluationController(SourceEvaluationPreviewService sourceEvaluationPreviewService) {
        this.sourceEvaluationPreviewService = sourceEvaluationPreviewService;
    }

    @PostMapping("/api/admin/source-evaluation/preview")
    public SourceEvaluationPreviewService.SourceEvaluationPreviewResponse preview(
            @RequestBody SourceEvaluationPreviewService.SourceEvaluationPreviewRequest request
    ) {
        try {
            return sourceEvaluationPreviewService.preview(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/api/admin/source-evaluation/configured")
    public SourceEvaluationPreviewService.ConfiguredSourceEvaluationResponse configured(
            @RequestBody(required = false) SourceEvaluationPreviewService.ConfiguredSourceEvaluationRequest request
    ) {
        return sourceEvaluationPreviewService.previewConfigured(request);
    }
}
