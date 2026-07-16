package com.parsernews.web;

import com.parsernews.service.CandidateRecomputeService;
import com.parsernews.service.EventReanalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminCandidateController {
    private final CandidateRecomputeService candidateRecomputeService;
    private final EventReanalysisService eventReanalysisService;

    public AdminCandidateController(
            CandidateRecomputeService candidateRecomputeService,
            EventReanalysisService eventReanalysisService
    ) {
        this.candidateRecomputeService = candidateRecomputeService;
        this.eventReanalysisService = eventReanalysisService;
    }

    @PostMapping("/api/admin/recompute-candidates")
    public CandidateRecomputeService.RecomputeSummary recomputeCandidates() {
        return candidateRecomputeService.recomputeCandidates();
    }

    /**
     * Replays the current rules over stored events, so articles collected before a rules fix get
     * the verdict they would get today. Defaults to a dry run: it reports what would change and
     * writes nothing until {@code apply=true}, because this rewrites stored verdicts in bulk.
     */
    @PostMapping("/api/admin/reanalyze-events")
    public EventReanalysisService.ReanalysisSummary reanalyzeEvents(
            @RequestParam(defaultValue = "false") boolean apply
    ) {
        return eventReanalysisService.reanalyze(!apply);
    }
}
