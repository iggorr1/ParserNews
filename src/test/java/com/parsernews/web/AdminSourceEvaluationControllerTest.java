package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.service.SourceEvaluationPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSourceEvaluationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class AdminSourceEvaluationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceEvaluationPreviewService sourceEvaluationPreviewService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void previewRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/admin/source-evaluation/preview")
                        .contentType("application/json")
                        .content("""
                                {"name":"Test","url":"https://example.com/rss.xml","maxItems":50}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previewReturnsSummary() throws Exception {
        when(sourceEvaluationPreviewService.preview(any())).thenReturn(new SourceEvaluationPreviewService.SourceEvaluationPreviewResponse(
                "Test source",
                "https://example.com/rss.xml",
                1,
                1,
                1,
                0,
                List.of(),
                SourceEvaluationPreviewService.Recommendation.KEEP,
                List.of()
        ));

        mockMvc.perform(post("/api/admin/source-evaluation/preview")
                        .with(httpBasic("tester", "secret"))
                        .contentType("application/json")
                        .content("""
                                {"name":"Test source","url":"https://example.com/rss.xml","maxItems":50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceName").value("Test source"))
                .andExpect(jsonPath("$.fetchedCount").value(1))
                .andExpect(jsonPath("$.strictCandidateCount").value(1))
                .andExpect(jsonPath("$.recommendation").value("KEEP"));
    }

    @Test
    void invalidUrlReturnsBadRequest() throws Exception {
        when(sourceEvaluationPreviewService.preview(any()))
                .thenThrow(new IllegalArgumentException("Source URL must be a valid HTTPS URL."));

        mockMvc.perform(post("/api/admin/source-evaluation/preview")
                        .with(httpBasic("tester", "secret"))
                        .contentType("application/json")
                        .content("""
                                {"name":"Bad","url":"ftp://example.com/rss.xml","maxItems":50}
                                """))
                .andExpect(status().isBadRequest());
    }
}
