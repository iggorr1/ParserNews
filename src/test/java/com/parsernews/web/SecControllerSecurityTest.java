package com.parsernews.web;

import com.parsernews.config.SecurityConfig;
import com.parsernews.persistence.SecFilingRepository;
import com.parsernews.service.NewsScannerService;
import com.parsernews.service.SafetyGuardService;
import com.parsernews.service.SecWatchlistScanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "parsernews.auth.enabled=true",
        "parsernews.auth.username=tester",
        "parsernews.auth.password=secret"
})
class SecControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecWatchlistScanner secWatchlistScanner;

    @MockitoBean
    private SecFilingRepository filingRepository;

    @MockitoBean
    private NewsScannerService newsScannerService;

    @MockitoBean
    private SafetyGuardService safetyGuardService;

    @Test
    void secEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/sec/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedSecStatusReturnsOk() throws Exception {
        when(secWatchlistScanner.status()).thenReturn(new SecWatchlistScanner.SecStatus(false, 0, 20, 0));

        mockMvc.perform(get("/api/sec/status").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.maxFilingsPerCik").value(20));
    }

    @Test
    void authenticatedSecScanReturnsSummary() throws Exception {
        when(secWatchlistScanner.scan()).thenReturn(new SecWatchlistScanner.SecScanSummary(
                true,
                1,
                4,
                3,
                2,
                1,
                "SEC scan completed."
        ));

        mockMvc.perform(post("/api/sec/scan").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedFilings").value(2))
                .andExpect(jsonPath("$.duplicatesSkipped").value(1));
    }
}
