package com.parsernews.web;

import com.parsernews.service.CandidateRecomputeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminCandidateController {
    private final CandidateRecomputeService candidateRecomputeService;

    public AdminCandidateController(CandidateRecomputeService candidateRecomputeService) {
        this.candidateRecomputeService = candidateRecomputeService;
    }

    @PostMapping("/api/admin/recompute-candidates")
    public CandidateRecomputeService.RecomputeSummary recomputeCandidates() {
        return candidateRecomputeService.recomputeCandidates();
    }
}
