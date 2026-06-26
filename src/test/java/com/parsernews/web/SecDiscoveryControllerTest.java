package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.service.SecDiscoveryScanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecDiscoveryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class SecDiscoveryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecDiscoveryScanner discoveryScanner;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void statusRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/sec/discovery/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusReturnsConfigAndLastRun() throws Exception {
        when(discoveryScanner.status()).thenReturn(new SecDiscoveryScanner.SecDiscoveryStatus(
                false,
                false,
                List.of("8-K", "SC TO-T"),
                50,
                false,
                Instant.parse("2026-06-24T12:00:00Z"),
                "SUCCESS",
                null,
                "SEC discovery is disabled."
        ));

        mockMvc.perform(get("/api/sec/discovery/status").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.forms[0]").value("8-K"))
                .andExpect(jsonPath("$.warning").value("SEC discovery is disabled."));
    }

    @Test
    void scanReturnsSummary() throws Exception {
        when(discoveryScanner.scan()).thenReturn(new SecDiscoveryScanner.SecDiscoverySummary(
                true,
                true,
                Instant.parse("2026-06-24T12:00:00Z"),
                Instant.parse("2026-06-24T12:00:02Z"),
                3,
                2,
                1,
                2,
                0,
                List.of(),
                2000
        ));

        mockMvc.perform(post("/api/sec/discovery/scan")
                        .with(httpBasic("tester", "secret"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.scannedFilings").value(3))
                .andExpect(jsonPath("$.newFilings").value(2))
                .andExpect(jsonPath("$.createdOrUpdatedDealGroups").value(2));
    }
}
