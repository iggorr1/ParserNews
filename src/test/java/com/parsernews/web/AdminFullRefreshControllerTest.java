package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.model.ScanSummary;
import com.parsernews.service.CandidateRecomputeService;
import com.parsernews.service.FullRefreshService;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.service.SecFilingDocumentFetcher;
import com.parsernews.service.SecWatchlistScanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminFullRefreshController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class AdminFullRefreshControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FullRefreshService fullRefreshService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void fullRefreshRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/admin/full-refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullRefreshReturnsSummary() throws Exception {
        when(fullRefreshService.fullRefresh()).thenReturn(new FullRefreshService.FullRefreshSummary(
                Instant.parse("2026-06-21T10:00:00Z"),
                Instant.parse("2026-06-21T10:00:10Z"),
                new ScanSummary(10, 8, 2, 0, 0, 0),
                new SecWatchlistScanner.SecScanSummary(true, 2, 20, 4, 3, 1, "SEC scan completed."),
                new SecFilingDocumentFetcher.SecDocumentFetchSummary(3, 2, 0, 1),
                new CandidateRecomputeService.RecomputeSummary(5, 2, 1, 1, 1, 2, 1, 0),
                List.of("SEC warning"),
                List.of(),
                true
        ));

        mockMvc.perform(post("/api/admin/full-refresh").with(user("tester").roles("ADMIN", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rssScanSummary.totalRead").value(10))
                .andExpect(jsonPath("$.secScanSummary.savedFilings").value(3))
                .andExpect(jsonPath("$.secDocumentFetchSummary.fetchedCount").value(2))
                .andExpect(jsonPath("$.recomputeSummary.updatedEvents").value(2))
                .andExpect(jsonPath("$.warnings[0]").value("SEC warning"));
    }
}
