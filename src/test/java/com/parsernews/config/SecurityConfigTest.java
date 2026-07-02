package com.parsernews.config;

import com.parsernews.service.StatusService;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.web.StatusController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class SecurityConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusService statusService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void unauthenticatedApiRequestReturnsUnauthorizedWhenAuthEnabled() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedApiRequestReturnsOk() throws Exception {
        when(statusService.status()).thenReturn(new StatusService.StatusResponse(
                StatusService.HealthStatus.OK,
                new StatusService.ConfigSummary(true, false, false, false),
                null,
                new StatusService.ArticleEventStats(0, 0, 0, 0, 0),
                new StatusService.AlertStats(0, 0)
        ));

        mockMvc.perform(get("/api/status").with(user("tester").roles("ADMIN", "VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedPostIsNotBlockedByCsrf() throws Exception {
        mockMvc.perform(post("/api/status").with(user("tester").roles("ADMIN", "VIEWER")))
                .andExpect(status().isMethodNotAllowed());
    }
}
