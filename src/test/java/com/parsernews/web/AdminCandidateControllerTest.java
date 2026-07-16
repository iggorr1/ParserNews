package com.parsernews.web;

import com.parsernews.service.CandidateRecomputeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminCandidateControllerTest {
    @Test
    void recomputeCandidatesReturnsSummaryCounts() {
        CandidateRecomputeService service = mock(CandidateRecomputeService.class);
        CandidateRecomputeService.RecomputeSummary summary = new CandidateRecomputeService.RecomputeSummary(
                4,
                3,
                1,
                1,
                1,
                1,
                1,
                1
        );
        when(service.recomputeCandidates()).thenReturn(summary);
        AdminCandidateController controller = new AdminCandidateController(
                service, mock(com.parsernews.service.EventReanalysisService.class));

        CandidateRecomputeService.RecomputeSummary response = controller.recomputeCandidates();

        assertThat(response.scannedEvents()).isEqualTo(4);
        assertThat(response.updatedEvents()).isEqualTo(3);
        assertThat(response.highCount()).isEqualTo(1);
        assertThat(response.mediumCount()).isEqualTo(1);
        assertThat(response.lowCount()).isEqualTo(1);
        assertThat(response.noneCount()).isEqualTo(1);
        assertThat(response.alertEligibleCount()).isEqualTo(1);
        assertThat(response.alreadyQueuedCount()).isEqualTo(1);
    }
}
