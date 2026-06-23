package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.service.SchedulerStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchedulerStatusController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class SchedulerStatusControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchedulerStatusService schedulerStatusService;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void schedulerStatusRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/admin/scheduler-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void schedulerStatusReturnsDisabledDefaultSummary() throws Exception {
        when(schedulerStatusService.status()).thenReturn(new SchedulerStatusService.SchedulerStatusResponse(
                true,
                null,
                false,
                false,
                null,
                null,
                null,
                List.of(),
                null,
                "SCHEDULED_FULL_REFRESH",
                900000,
                null,
                false,
                false,
                "Full Refresh runs only when you click the button."
        ));

        mockMvc.perform(get("/api/admin/scheduler-status").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rssMonitoringEnabled").value(true))
                .andExpect(jsonPath("$.fullRefreshSchedulerEnabled").value(false))
                .andExpect(jsonPath("$.fullRefreshSchedulerRunning").value(false))
                .andExpect(jsonPath("$.scheduledFullRefreshTriggerType").value("SCHEDULED_FULL_REFRESH"))
                .andExpect(jsonPath("$.fullRefreshFixedDelayMs").value(900000))
                .andExpect(jsonPath("$.telegramEnabled").value(false))
                .andExpect(jsonPath("$.dispatchEnabled").value(false))
                .andExpect(jsonPath("$.message").value("Full Refresh runs only when you click the button."));
    }
}
