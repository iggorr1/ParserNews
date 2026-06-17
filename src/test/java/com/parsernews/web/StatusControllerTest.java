package com.parsernews.web;

import com.parsernews.service.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusControllerTest {
    @Test
    void statusEndpointReturnsOkAndConfigFlags() throws Exception {
        StatusService statusService = mock(StatusService.class);
        when(statusService.status()).thenReturn(new StatusService.StatusResponse(
                StatusService.HealthStatus.OK,
                new StatusService.ConfigSummary(true, false, false, false),
                null,
                new StatusService.ArticleEventStats(0, 0, 0, 0, 0),
                new StatusService.AlertStats(0, 0)
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StatusController(statusService)).build();

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.config.scannerMonitoringEnabled").value(true))
                .andExpect(jsonPath("$.config.alertDispatchEnabled").value(false))
                .andExpect(jsonPath("$.config.telegramEnabled").value(false))
                .andExpect(jsonPath("$.config.telegramConfigured").value(false));
    }
}
